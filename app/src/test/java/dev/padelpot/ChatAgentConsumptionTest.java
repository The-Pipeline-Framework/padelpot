package dev.padelpot;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.padelpot.persistence.AggregateQueries;
import dev.padelpot.domain.ChatIngress;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** A real PadelPot entrypoint consumes the Block while existing match authority stays separate. */
@QuarkusTest
class ChatAgentConsumptionTest {
  @Inject DemoSession session;
  @Inject AggregateQueries history;
  @Inject PadelApplication application;

  @Test
  void hydratesAcceptedOwnerHistoryAtNewRootAndKeepsOwnersIsolated() throws Exception {
    session.reset(false);
    String first = send("alba", "I enjoy quiet courts");
    TopologyTest.awaitStatus(first, "SUCCEEDED");
    assertEquals(List.of("owner", "agent"), history.allHistory("padelpot-demo", "alba")
        .stream().map(AggregateQueries.HistoryTurn::speaker).toList());

    String followUp = send("alba", "What did I say before?");
    TopologyTest.awaitStatus(followUp, "SUCCEEDED");
    assertTrue(history.allHistory("padelpot-demo", "alba").stream()
        .anyMatch(turn -> turn.speaker().equals("agent")
            && turn.text().contains("I enjoy quiet courts")));

    String otherOwner = send("nico", "What did I say before?");
    TopologyTest.awaitStatus(otherOwner, "SUCCEEDED");
    assertTrue(history.allHistory("padelpot-demo", "nico").stream()
        .noneMatch(turn -> turn.text().contains("I enjoy quiet courts")));
  }

  @Test
  void oneTypedQueryIsCapturedAndSynthesizedIntoANaturalReply() throws Exception {
    session.reset(false);
    String execution = send("alba", "Where is the court?");
    TopologyTest.awaitStatus(execution, "SUCCEEDED");

    assertTrue(history.allHistory("padelpot-demo", "alba").stream()
        .anyMatch(turn -> turn.speaker().equals("agent")
            && turn.text().contains("fictional demo")
            && turn.text().contains("no real court address")));
  }

  @Test
  void clarificationAndLaterCorrectionUseTheSameOwnerConversation() throws Exception {
    session.reset(false);
    String first = send("alba", "Could we arrange a match?");
    TopologyTest.awaitStatus(first, "SUCCEEDED");
    assertTrue(history.allHistory("padelpot-demo", "alba").stream()
        .anyMatch(turn -> turn.speaker().equals("agent")
            && turn.text().contains("open a new match or find one")));

    var state = application.chatContext(new ChatIngress(
        "alba:correction-probe", "alba", "Actually, find one instead", ""));
    assertTrue(state.evidence().stream().anyMatch(entry ->
        entry.text().contains("Could we arrange a match?")));
    assertTrue(state.evidence().stream().anyMatch(entry ->
        entry.text().contains("open a new match or find one")));

    String correction = send("alba", "Actually, find one instead");
    TopologyTest.awaitStatus(correction, "SUCCEEDED");
    assertTrue(session.snapshot().matches.isEmpty());
  }

  @Test
  void pendingApprovalQuestionsUseChatAgentAndTypedCountersResumeAwait() throws Exception {
    session.reset(false);
    var match = new DemoSession.Match();
    match.id = "pending-chat-match";
    match.organizer = "alba";
    match.hour = 19;
    match.style = "competitive";
    match.area = "Barcelona";
    match.date = "Synthetic Thursday";
    match.level = "intermediate";
    match.players.put("alba", "left");
    session.snapshot().matches.put(match.id, match);

    String negotiation = send("luz", "Find an existing competitive match for me");
    var approval = TopologyTest.awaitPending("luz");
    assertEquals("participation", approval.get("kind"));
    String interactionId = (String) approval.get("id");

    String question = sendWithReview("luz", interactionId,
        "Left it is. Would someone give me a lift to the club?");
    TopologyTest.awaitStatus(question, "SUCCEEDED");
    assertEquals(interactionId, TopologyTest.awaitPending("luz").get("id"));
    assertFalse(match.players.containsKey("luz"));
    assertTrue(session.player("luz").chat.stream().anyMatch(turn ->
        turn.role().equals("agent") && turn.text().contains("cannot arrange a lift")));

    String counter = sendWithReview("luz", interactionId,
        "Could we switch to the right instead?");
    TopologyTest.awaitStatus(counter, "SUCCEEDED");
    assertFalse(match.players.containsKey("luz"), "A counter must not approve participation");
    assertTrue(session.player("luz").chat.stream().anyMatch(turn ->
        turn.role().equals("agent") && turn.text().contains("sent your counterproposal")));
    for (int attempt = 0; attempt < 100; attempt++) {
      var current = given().get("/api/owners/luz").then().statusCode(200)
          .extract().<List<Map<String, Object>>>path("pending");
      if (!current.isEmpty() && !interactionId.equals(current.getFirst().get("id"))) {
        given().contentType("application/json")
            .body(Map.of("ownerId", "luz", "messageId", UUID.randomUUID().toString(),
                "text", "", "interactionId", current.getFirst().get("id"),
                "choice", "reject"))
            .post("/api/messages").then().statusCode(200);
        TopologyTest.awaitStatus(negotiation, "SUCCEEDED");
        return;
      }
      Thread.sleep(100);
    }
    throw new AssertionError("The typed counter did not resume native negotiation");
  }

  private static String sendWithReview(String owner, String interactionId, String text) {
    return given().contentType("application/json")
        .body(Map.of("ownerId", owner, "messageId", UUID.randomUUID().toString(),
            "text", text, "interactionId", interactionId))
        .post("/api/messages").then().statusCode(200).extract().path("executionId");
  }

  private static String send(String owner, String text) {
    return given().contentType("application/json")
        .body(Map.of("ownerId", owner, "messageId", UUID.randomUUID().toString(), "text", text))
        .post("/api/messages").then().statusCode(200).extract().path("executionId");
  }
}
