package dev.padelpot;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.*;

import dev.padelpot.persistence.AggregateQueries;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.*;
import org.junit.jupiter.api.Test;

@QuarkusTest
class NativePersistenceTest {
  @Inject DemoSession session;
  @Inject AggregateQueries aggregates;
  @Inject org.pipelineframework.PipelineExecutionService pipeline;

  @Test
  void onlyApprovedOwnerAggregateIsPersistedAndReloaded() throws Exception {
    session.reset(true);
    String execution =
        given()
            .contentType("application/json")
            .body(
                Map.of(
                    "ownerId",
                    "luz",
                    "messageId",
                    UUID.randomUUID().toString(),
                    "text",
                    "My level is intermediate"))
            .post("/api/messages")
            .then()
            .statusCode(200)
            .extract()
            .path("executionId");
    var pending = TopologyTest.awaitPending("luz");
    assertTrue(aggregates.owners().isEmpty(), "A proposed preference must not be durable");
    given()
        .contentType("application/json")
        .body(
            Map.of(
                "ownerId",
                "luz",
                "messageId",
                UUID.randomUUID().toString(),
                "interactionId",
                pending.get("id"),
                "choice",
                "approve",
                "text",
                ""))
        .post("/api/messages")
        .then()
        .statusCode(200);
    TopologyTest.awaitStatus(execution, "SUCCEEDED");
    var owner = aggregates.owners().getFirst();
    assertEquals("luz", owner.id());
    assertEquals(2, owner.version());
    assertEquals(
        "declared",
        owner.facts().stream()
            .filter(f -> f.field().equals("level"))
            .findFirst()
            .orElseThrow()
            .source());
    session.load();
    assertEquals("intermediate", session.player("luz").value("level"));
    assertEquals(2, session.player("luz").version);
    given().delete("/api/owners/luz/persona/level").then().statusCode(200);
    assertTrue(
        aggregates.owners().getFirst().facts().stream().noneMatch(f -> f.field().equals("level")));
    session.load();
    assertEquals("", session.player("luz").value("level"));
    given()
        .contentType("application/json")
        .body(Map.of("otherId", "nico", "blocked", true))
        .post("/api/owners/luz/block")
        .then()
        .statusCode(200);
    session.load();
    assertTrue(session.player("luz").blocked.contains("nico"));
  }

  @Test
  void candidateAcknowledgementKeepsOwnerAndRequestAfterPersistence() throws Exception {
    session.reset(false);
    var match = new DemoSession.Match();
    match.id = "candidate-match";
    match.organizer = "alba";
    match.hour = 19;
    match.style = "competitive";
    match.area = "Barcelona";
    match.date = "Synthetic Thursday";
    match.level = "intermediate";
    match.players.put("alba", "left");
    session.snapshot().matches.put(match.id, match);
    String message = UUID.randomUUID().toString();
    String execution =
        given()
            .contentType("application/json")
            .body(
                Map.of("ownerId", "nico", "messageId", message, "text", "Find a competitive match"))
            .post("/api/messages")
            .then()
            .statusCode(200)
            .extract()
            .path("executionId");
    for (String kind : List.of("clarify", "participation")) {
      var pending = TopologyTest.awaitPending("nico");
      assertEquals(kind, pending.get("kind"));
      given()
          .contentType("application/json")
          .body(
              Map.of(
                  "ownerId",
                  "nico",
                  "messageId",
                  UUID.randomUUID().toString(),
                  "interactionId",
                  pending.get("id"),
                  "choice",
                  "approve",
                  "text",
                  ""))
          .post("/api/messages")
          .then()
          .statusCode(200);
      if (kind.equals("clarify")) {
        for (int attempt = 0; attempt < 100; attempt++) {
          var current =
              given()
                  .get("/api/owners/nico")
                  .then()
                  .statusCode(200)
                  .extract()
                  .<List<Map<String, Object>>>path("pending");
          if (current.isEmpty() || !current.getFirst().get("id").equals(pending.get("id"))) break;
          Thread.sleep(20);
        }
      }
    }
    TopologyTest.awaitStatus(execution, "SUCCEEDED");
    var result =
        pipeline
            .<dev.padelpot.domain.Result>getExecutionResult(
                ChannelAdapter.TENANT, execution, dev.padelpot.domain.Result.class, false)
            .await()
            .indefinitely();
    assertEquals("nico", result.ownerId());
    assertEquals("nico:" + message, result.requestId());
    assertEquals("CONFIRMED", result.status());
    assertEquals("nico", aggregates.matches().getFirst().change().ownerId());
    assertEquals(2, aggregates.matches().getFirst().players().size());
  }

  @Test
  void simulatedPlayPersistsMatchAndFourObservedOwnersInOneRoot() {
    session.reset(false);
    var match = new DemoSession.Match();
    match.id = "observed-match";
    match.organizer = "alba";
    match.hour = 19;
    match.style = "competitive";
    match.area = "Barcelona";
    match.date = "Synthetic Thursday";
    match.level = "intermediate";
    match.status = "CONFIRMED";
    match.players.putAll(Map.of("alba", "left", "nico", "right", "luz", "left", "teo", "right"));
    session.snapshot().matches.put(match.id, match);
    given()
        .contentType("application/json")
        .body(Map.of("ownerId", "alba"))
        .post("/api/matches/observed-match/simulate")
        .then()
        .statusCode(200);
    assertEquals(
        1, session.snapshot().events.stream().filter(e -> e.type().equals("ROOT_STARTED")).count());
    assertEquals(4, aggregates.owners().size());
    assertEquals(1, aggregates.matches().size());
    session.load();
    assertTrue(session.snapshot().matches.get(match.id).played);
    for (var owner : session.snapshot().players.values()) {
      assertEquals("19", owner.value("lastPlayedHour"));
      assertEquals("observed", owner.facts.get("lastPlayedHour").source);
    }
    given().delete("/api/owners/luz/persona/lastPlayedHour").then().statusCode(200);
    session.load();
    assertEquals("", session.player("luz").value("lastPlayedHour"));
    assertEquals("19", session.player("nico").value("lastPlayedHour"));
    assertEquals(4, session.snapshot().matches.get(match.id).players.size());
  }
}
