package dev.padelpot;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.padelpot.persistence.AggregateQueries;
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

  private static String send(String owner, String text) {
    return given().contentType("application/json")
        .body(Map.of("ownerId", owner, "messageId", UUID.randomUUID().toString(), "text", text))
        .post("/api/messages/chat-agent").then().statusCode(200).extract().path("executionId");
  }
}
