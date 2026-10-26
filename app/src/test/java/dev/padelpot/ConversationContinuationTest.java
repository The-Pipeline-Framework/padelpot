package dev.padelpot;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.*;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.*;
import org.junit.jupiter.api.Test;

@QuarkusTest
class ConversationContinuationTest {
  @Inject DemoSession session;

  @Test
  void naturalClarificationThenFindStillRequiresParticipationApproval() throws Exception {
    var match = seedMatch("luz");
    String clarification = send("luz", "Could we arrange a match?");
    TopologyTest.awaitStatus(clarification, "SUCCEEDED");
    assertTrue(session.player("luz").chat.stream()
        .anyMatch(turn -> turn.role().equals("agent")
            && turn.text().contains("open a new match or find one")));

    String execution = send("luz", "Actually, find one instead");
    Map<String, Object> approval = TopologyTest.awaitPending("luz");
    assertEquals("participation", approval.get("kind"));
    assertEquals(execution, approval.get("executionId"));
    assertEquals(3, match.players.size(), "A model handoff cannot commit participation");
    approve("luz", approval);
    TopologyTest.awaitStatus(execution, "SUCCEEDED");
    assertEquals(4, match.players.size());
  }

  @Test
  void scopedFlexibilityAnswerReachesParticipationApprovalInTheSameRoot() throws Exception {
    var match = seedMatch("nico");
    String execution = send("nico", "Find me a competitive match this Thursday after work.");
    Map<String, Object> permission = TopologyTest.awaitPending("nico");
    assertEquals("clarify", permission.get("kind"));
    assertEquals(execution, permission.get("executionId"));
    assertEquals(3, match.players.size());

    given().contentType("application/json")
        .body(Map.of("ownerId", "nico", "messageId", UUID.randomUUID().toString(),
            "text", "I'm flexible", "interactionId", permission.get("id")))
        .post("/api/messages").then().statusCode(200);
    Map<String, Object> approval = awaitParticipation("nico", execution);
    assertEquals(3, match.players.size(), "Time permission is not participation approval");
    approve("nico", approval);
    TopologyTest.awaitStatus(execution, "SUCCEEDED");
    assertEquals(4, match.players.size());
  }

  private DemoSession.Match seedMatch(String owner) {
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
    match.players.put(owner.equals("luz") ? "nico" : "luz", owner.equals("luz") ? "right" : "left");
    match.players.put("teo", "right");
    session.snapshot().matches.put(match.id, match);
    return match;
  }

  private static String send(String owner, String text) {
    return given().contentType("application/json")
        .body(Map.of("ownerId", owner, "messageId", UUID.randomUUID().toString(), "text", text))
        .post("/api/messages").then().statusCode(200).extract().path("executionId");
  }

  private static Map<String, Object> awaitParticipation(String owner, String execution) throws Exception {
    for (int attempt = 0; attempt < 200; attempt++) {
      List<Map<String, Object>> pending = given().get("/api/owners/" + owner)
          .then().statusCode(200).extract().path("pending");
      for (var review : pending) {
        if (execution.equals(review.get("executionId"))
            && "participation".equals(review.get("kind"))) return review;
      }
      Thread.sleep(50);
    }
    throw new AssertionError("The resumed search did not reach participation approval");
  }

  private static void approve(String owner, Map<String, Object> review) {
    given().contentType("application/json")
        .body(Map.of("ownerId", owner, "messageId", UUID.randomUUID().toString(),
            "text", "", "interactionId", review.get("id"), "choice", "approve"))
        .post("/api/messages").then().statusCode(200);
  }
}
