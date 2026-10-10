package dev.padelpot;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import dev.padelpot.domain.*;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import jakarta.inject.Inject;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

@QuarkusTest
class ConversationContinuationTest {
  @Inject DemoSession session;
  @InjectSpy PadelApplication application;

  @Test
  void locationAnswerResumesTheSearchAndStillRequiresParticipationApproval() throws Exception {
    session.reset(false);
    var match = new DemoSession.Match();
    match.id = "conversation-match";
    match.organizer = "alba";
    match.hour = 19;
    match.style = "competitive";
    match.level = "intermediate";
    match.area = "Barcelona";
    match.date = "Synthetic Thursday";
    match.players.put("alba", "left");
    match.players.put("nico", "right");
    match.players.put("teo", "right");
    session.snapshot().matches.put(match.id, match);
    String request = "Find me a competitive match this Thursday after work.";
    String answer = "anywhere in Barcelona is fine by me";
    var first = new AtomicBoolean(true);
    var resumedWithContext = new AtomicBoolean(false);
    doAnswer(
            invocation -> {
              ModelTurn turn = invocation.getArgument(0);
              if (turn.state().mode().equals("chat")) {
                if (first.getAndSet(false))
                  return application.apply(
                      new ModelTurn(
                          turn.state(),
                          new AgentDecision(
                              "message", 0, "", "", "", "", "Which broad area works for you?")));
                resumedWithContext.set(
                    turn.state().ownerText().contains(request)
                        && turn.state().ownerText().contains(answer)
                        && turn.state().lastAction().equals("conversation_answered"));
              }
              return invocation.callRealMethod();
            })
        .when(application)
        .apply(any(ModelTurn.class));
    String execution =
        given()
            .contentType("application/json")
            .body(
                Map.of(
                    "ownerId", "luz", "messageId", UUID.randomUUID().toString(), "text", request))
            .post("/api/messages")
            .then()
            .statusCode(200)
            .extract()
            .path("executionId");
    var question = TopologyTest.awaitPending("luz");
    assertEquals("conversation", question.get("kind"));
    given()
        .contentType("application/json")
        .body(
            Map.of(
                "ownerId",
                "luz",
                "messageId",
                UUID.randomUUID().toString(),
                "text",
                answer,
                "interactionId",
                question.get("id"),
                "choice",
                "clarify"))
        .post("/api/messages")
        .then()
        .statusCode(200);
    Map<String, Object> approval = null;
    for (int i = 0; i < 200; i++) {
      List<Map<String, Object>> pending =
          given().get("/api/owners/luz").then().statusCode(200).extract().path("pending");
      if (!pending.isEmpty() && pending.getFirst().get("kind").equals("participation")) {
        approval = pending.getFirst();
        break;
      }
      Thread.sleep(50);
    }
    assertNotNull(approval, "The resumed search must reach participation approval");
    assertTrue(resumedWithContext.get());
    assertEquals(execution, approval.get("executionId"));
    assertEquals(3, match.players.size(), "A location answer cannot commit participation");
    given()
        .contentType("application/json")
        .body(
            Map.of(
                "ownerId",
                "luz",
                "messageId",
                UUID.randomUUID().toString(),
                "text",
                "",
                "interactionId",
                approval.get("id"),
                "choice",
                "approve"))
        .post("/api/messages")
        .then()
        .statusCode(200);
    TopologyTest.awaitStatus(execution, "SUCCEEDED");
    assertEquals("CONFIRMED", match.status);
    assertEquals(4, match.players.size());
    assertEquals(
        1, session.snapshot().events.stream().filter(e -> e.type().equals("ROOT_STARTED")).count());
  }
}
