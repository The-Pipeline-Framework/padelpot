package dev.padelpot;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.*;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.*;
import org.junit.jupiter.api.Test;

@io.quarkus.test.junit.TestProfile(TopologyTest.Telemetry.class)
@QuarkusTest
public class TopologyTest {
  public static class Telemetry implements io.quarkus.test.junit.QuarkusTestProfile {
    public Map<String, String> getConfigOverrides() {
      return Map.of(
          "pipeline.telemetry.replay.enabled",
          "true",
          "pipeline.telemetry.replay.file.path",
          "target/topology-replay");
    }
  }

  @Inject DemoStore store;

  @Test
  void oneRequestOneRootAndCorrelatedHumanResume() throws Exception {
    java.nio.file.Path replay = java.nio.file.Path.of("target/topology-replay");
    if (java.nio.file.Files.exists(replay))
      try (var files = java.nio.file.Files.list(replay)) {
        for (var f : files.toList()) java.nio.file.Files.delete(f);
      }
    store.reset(false);
    store.event("ROOT_STARTED", Map.of("executionId", "historical-root-lost-on-restart"));
    given()
        .contentType("application/json")
        .body(Map.of("onboarding", false))
        .post("/api/demo/reset")
        .then()
        .statusCode(200);
    String id = "topology-open";
    String execution =
        given()
            .contentType("application/json")
            .body(
                Map.of(
                    "ownerId",
                    "alba",
                    "messageId",
                    id,
                    "text",
                    "Open a competitive match on Thursday at 19:00. I play left."))
            .post("/api/messages")
            .then()
            .statusCode(200)
            .extract()
            .path("executionId");
    Map<String, Object> interaction = awaitPending("alba");
    assertEquals(
        1, store.snapshot().events.stream().filter(e -> e.type().equals("ROOT_STARTED")).count());
    assertTrue(store.snapshot().matches.isEmpty(), "No participation without approval");
    assertEquals(execution, interaction.get("executionId"));
    given()
        .contentType("application/json")
        .body(
            Map.of(
                "ownerId",
                "alba",
                "messageId",
                "malformed-human",
                "text",
                "",
                "interactionId",
                interaction.get("id")))
        .post("/api/messages")
        .then()
        .statusCode(400);
    given()
        .contentType("application/json")
        .body(
            Map.of(
                "ownerId",
                "nico",
                "messageId",
                "wrong-owner",
                "text",
                "",
                "interactionId",
                interaction.get("id"),
                "choice",
                "approve"))
        .post("/api/messages")
        .then()
        .statusCode(403);
    given()
        .contentType("application/json")
        .body(
            Map.of(
                "ownerId",
                "alba",
                "messageId",
                "approve-open",
                "text",
                "",
                "interactionId",
                interaction.get("id"),
                "choice",
                "approve"))
        .post("/api/messages")
        .then()
        .statusCode(200);
    awaitStatus(execution, "SUCCEEDED");
    given()
        .contentType("application/json")
        .body(
            Map.of(
                "ownerId",
                "alba",
                "messageId",
                "approve-open",
                "text",
                "",
                "interactionId",
                interaction.get("id"),
                "choice",
                "approve"))
        .post("/api/messages")
        .then()
        .statusCode(409);
    String duplicated =
        given()
            .contentType("application/json")
            .body(
                Map.of(
                    "ownerId",
                    "alba",
                    "messageId",
                    id,
                    "text",
                    "Open a competitive match on Thursday at 19:00. I play left."))
            .post("/api/messages")
            .then()
            .statusCode(200)
            .extract()
            .path("executionId");
    assertEquals(execution, duplicated, "Duplicate delivery must not admit a new root");
    assertEquals(1, store.snapshot().matches.values().iterator().next().players.size());
    long documents = 0;
    for (int n = 0; n < 100; n++) {
      if (java.nio.file.Files.exists(replay))
        try (var files = java.nio.file.Files.list(replay)) {
          documents = files.filter(f -> f.toString().endsWith(".json")).count();
        }
      if (documents == 3) break;
      Thread.sleep(100);
    }
    assertEquals(
        3, documents, "Two execution segments and one native Await control document for one root");
    try (var files = java.nio.file.Files.list(replay)) {
      String nativeControl =
          java.nio.file.Files.readString(
              files
                  .filter(f -> f.getFileName().toString().contains("await-control"))
                  .findFirst()
                  .orElseThrow());
      assertTrue(
          nativeControl.contains(execution), "Native Await lineage identifies the admitted root");
    }
    assertEquals(
        1, store.snapshot().events.stream().filter(e -> e.type().equals("ROOT_STARTED")).count());
  }

  static Map<String, Object> awaitPending(String owner) throws Exception {
    for (int i = 0; i < 900; i++) {
      List<Map<String, Object>> p =
          given().get("/api/owners/" + owner).then().statusCode(200).extract().path("pending");
      if (!p.isEmpty()) return p.getFirst();
      Thread.sleep(100);
    }
    throw new AssertionError("No pending human interaction for " + owner);
  }

  static void awaitStatus(String id, String status) throws Exception {
    for (int i = 0; i < 900; i++) {
      String s =
          given().get("/api/executions/" + id).then().statusCode(200).extract().path("status");
      if (s.equals(status)) return;
      if (Set.of("FAILED", "DLQ").contains(s))
        throw new AssertionError(
            "Execution failed: " + given().get("/api/executions/" + id).asString());
      Thread.sleep(100);
    }
    throw new AssertionError("Execution did not reach " + status);
  }
}
