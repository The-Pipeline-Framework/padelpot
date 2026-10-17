package dev.padelpot;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.*;

import dev.padelpot.domain.Review;
import dev.padelpot.domain.TurnState;
import dev.padelpot.persistence.AggregateQueries;
import dev.padelpot.persistence.OwnerTurnMapper;
import dev.padelpot.persistence.ReviewTurnMapper;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class ConversationHistoryTest {
  @Inject DemoSession session;
  @Inject AggregateQueries history;
  @Inject PadelApplication application;
  @Inject ReviewTurnMapper reviewTurns;
  @Inject OwnerTurnMapper ownerTurns;
  private final Set<String> executions = new LinkedHashSet<>();

  @Test
  void reviewedPromptIdentityIsStableAndDistinguishesChangedPrompts() {
    session.reset(false);
    TurnState state = followUp("alba");
    var first = new Review(state, "conversation", "First question?", "", "");
    var changed = new Review(state, "conversation", "Different question?", "", "");
    assertEquals(reviewTurns.toExternal(first).eventId, reviewTurns.toExternal(first).eventId);
    assertNotEquals(reviewTurns.toExternal(first).eventId, reviewTurns.toExternal(changed).eventId);
  }

  @Test
  void organizerTurnBelongsToActingOwnersHistoryWithoutChangingRootOwner() {
    TurnState root = followUp("nico");
    TurnState organizer = new TurnState(
        root.requestId(), root.ownerId(), "alba", "negotiate", root.matchId(), root.turn(),
        root.deadline(), root.ownerVersion(), root.organizerVersion(), root.hour(),
        root.style(), root.side(), root.lastAction(), root.transcript(),
        "private organizer instruction", root.team());

    var saved = ownerTurns.toExternal(organizer);
    assertEquals("alba", saved.ownerId);
    assertEquals("alba", saved.conversationId);
    assertEquals("nico", saved.rootOwnerId);
    assertEquals("private organizer instruction", saved.text);
    assertEquals(organizer, ownerTurns.fromExternal(saved));
  }

  @Test
  void privacyScenarioKeepsOrganizersInstructionOutOfCandidateHistory() throws Exception {
    String execution = given()
        .contentType("application/json")
        .body(Map.of("scenario", "privacy"))
        .post("/api/demo/scenarios")
        .then()
        .statusCode(200)
        .extract()
        .path("executionId");
    executions.add(execution);
    String privateInstruction = "Synthetic privacy challenge";
    for (int attempt = 0; attempt < 100
        && history.allHistory("padelpot-demo", "alba").stream()
            .noneMatch(turn -> turn.text().contains(privateInstruction)); attempt++) {
      Thread.sleep(30);
    }

    assertTrue(history.allHistory("padelpot-demo", "alba").stream()
        .anyMatch(turn -> turn.text().contains(privateInstruction)));
    assertTrue(history.allHistory("padelpot-demo", "nico").stream()
        .noneMatch(turn -> turn.text().contains(privateInstruction)));
    assertTrue(application.contextWithHistory(followUp("nico")).recentHistory().stream()
        .noneMatch(turn -> turn.contains(privateInstruction)));
    session.load();
    assertTrue(session.player("nico").chat.stream()
        .noneMatch(turn -> turn.text().contains(privateInstruction)));
  }

  @AfterEach
  void resolveOwnedInteractions() throws Exception {
    for (String ownerId : List.of("alba", "nico")) {
      Map<String, Object> owner = given().get("/api/owners/" + ownerId).then().statusCode(200).extract().as(Map.class);
      for (Object item : (List<?>) owner.get("pending")) {
        Map<?, ?> pending = (Map<?, ?>) item;
        if (!executions.contains(pending.get("executionId"))) continue;
        given()
            .contentType("application/json")
            .body(Map.of(
                "ownerId", ownerId,
                "messageId", UUID.randomUUID().toString(),
                "interactionId", pending.get("id"),
                "choice", "reject",
                "text", ""))
            .post("/api/messages")
            .then()
            .statusCode(200);
      }
    }
  }

  @Test
  void approvedTurnsSurviveReloadAndStayWithinOwnerScope() throws Exception {
    session.reset(false);
    String request = UUID.randomUUID().toString();
    executions.add(given()
        .contentType("application/json")
        .body(Map.of("ownerId", "alba", "messageId", request, "text", "I enjoy quiet courts"))
        .post("/api/messages")
        .then()
        .statusCode(200)
        .extract()
        .path("executionId"));
    TopologyTest.awaitPending("alba");

    var saved = history.allHistory("padelpot-demo", "alba");
    assertEquals(List.of("owner", "agent"), saved.stream().map(AggregateQueries.HistoryTurn::speaker).toList());
    assertEquals("I enjoy quiet courts", saved.getFirst().text());
    assertTrue(saved.getLast().text().contains("level"));
    assertTrue(saved.getFirst().cursor() < saved.getLast().cursor());
    var firstPage = history.historyPage("padelpot-demo", "alba", 0, 1);
    assertEquals(List.of(saved.getFirst()), firstPage.turns());
    assertEquals(Optional.of(saved.getFirst().cursor()), firstPage.nextCursor());
    assertEquals(
        List.of(saved.getLast()),
        history.historyPage("padelpot-demo", "alba", firstPage.nextCursor().orElseThrow(), 1).turns());
    assertTrue(history.allHistory("padelpot-demo", "nico").isEmpty());
    assertTrue(history.allHistory("another-tenant", "alba").isEmpty());
    assertEquals(
        saved,
        history.allHistory("padelpot-demo", "alba"),
        "Reading history must have a stable order");

    given()
        .contentType("application/json")
        .body(Map.of("ownerId", "alba", "messageId", request, "text", "I enjoy quiet courts"))
        .post("/api/messages")
        .then()
        .statusCode(200);
    assertEquals(saved, history.allHistory("padelpot-demo", "alba"));

    session.load();
    assertTrue(session.player("alba").chat.stream().anyMatch(c -> c.text().equals("I enjoy quiet courts")));
    assertTrue(
        application.contextWithHistory(followUp("alba")).recentHistory().stream()
            .anyMatch(turn -> turn.equals("owner: I enjoy quiet courts")));
    assertTrue(application.contextWithHistory(followUp("nico")).recentHistory().isEmpty());

    String followUpExecution =
        given()
            .contentType("application/json")
            .body(Map.of(
                "ownerId", "alba", "messageId", UUID.randomUUID().toString(),
                "text", "What did I say before?"))
            .post("/api/messages")
            .then()
            .statusCode(200)
            .extract()
            .path("executionId");
    executions.add(followUpExecution);
    assertTrue(awaitPrompt(followUpExecution).contains("quiet courts"));
  }

  @Test
  void explicitCommandErasesOnlyTheRequestedOwnersApprovedTurns() throws Exception {
    session.reset(false);
    for (String owner : List.of("alba", "nico")) {
      executions.add(given()
          .contentType("application/json")
          .body(Map.of("ownerId", owner, "messageId", UUID.randomUUID().toString(), "text", "Hello"))
          .post("/api/messages")
          .then()
          .statusCode(200)
          .extract()
          .path("executionId"));
      TopologyTest.awaitPending(owner);
    }
    assertFalse(history.allHistory("padelpot-demo", "alba").isEmpty());
    assertFalse(history.allHistory("padelpot-demo", "nico").isEmpty());

    String execution =
        given()
            .header("Idempotency-Key", UUID.randomUUID().toString())
            .delete("/api/owners/alba/history")
            .then()
            .statusCode(200)
            .extract()
            .path("executionId");
    TopologyTest.awaitStatus(execution, "SUCCEEDED");
    assertTrue(history.allHistory("padelpot-demo", "alba").isEmpty());
    assertFalse(history.allHistory("padelpot-demo", "nico").isEmpty());
    assertTrue(application.contextWithHistory(followUp("alba")).recentHistory().isEmpty());
    assertFalse(application.contextWithHistory(followUp("nico")).recentHistory().isEmpty());
    session.load();
    assertFalse(session.player("alba").chat.stream().anyMatch(c -> c.text().equals("Hello")));
  }

  private TurnState followUp(String owner) {
    var player = session.player(owner);
    return new TurnState(
        owner + ":follow-up", owner, owner, "chat", "", 0,
        System.currentTimeMillis() + 900_000, player.version, 0L, 19,
        "competitive", Doubles.preferredSide(player), "", List.of(),
        "What did I say before?", "");
  }

  private String awaitPrompt(String executionId) throws Exception {
    for (int attempt = 0; attempt < 100; attempt++) {
      Map<String, Object> owner = given().get("/api/owners/alba").then().statusCode(200).extract().as(Map.class);
      for (Object item : (List<?>) owner.get("pending")) {
        Map<?, ?> pending = (Map<?, ?>) item;
        if (executionId.equals(pending.get("executionId"))) return String.valueOf(pending.get("prompt"));
      }
      Thread.sleep(30);
    }
    throw new AssertionError("Follow-up did not reach Await");
  }
}
