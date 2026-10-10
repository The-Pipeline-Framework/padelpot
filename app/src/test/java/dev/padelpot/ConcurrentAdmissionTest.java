package dev.padelpot;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import dev.padelpot.domain.TurnState;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import jakarta.inject.Inject;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;

@QuarkusTest
class ConcurrentAdmissionTest {
  @Inject DemoSession store;
  @InjectSpy PadelApplication application;

  @Test
  void fourOwnersAreAdmittedWhileAnotherRootIsRunning() throws Exception {
    store.reset(false);
    var entered = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    doAnswer(
            invocation -> {
              TurnState state = invocation.getArgument(0);
              if (state.ownerId().equals("alba")) {
                entered.countDown();
                if (!release.await(20, TimeUnit.SECONDS))
                  throw new AssertionError("Blocked root was not released");
              }
              return invocation.callRealMethod();
            })
        .when(application)
        .context(any(TurnState.class));

    Map<String, String> executions = new LinkedHashMap<>();
    try {
      String first = send("alba");
      executions.put("alba", first);
      assertTrue(entered.await(10, TimeUnit.SECONDS));
      assertEquals(
          "RUNNING",
          given().get("/api/executions/" + first).then().statusCode(200).extract().path("status"));
      // The old global guard rejected these requests with 409 while Alba was running.
      try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
        var owners = List.of("nico", "luz", "teo");
        var futures = new LinkedHashMap<String, Future<String>>();
        for (String owner : owners) futures.put(owner, pool.submit(() -> send(owner)));
        for (var entry : futures.entrySet())
          executions.put(entry.getKey(), entry.getValue().get(10, TimeUnit.SECONDS));
      }
      assertEquals(first, send("alba"), "Duplicate delivery must reuse the running root");
      given().get("/api/owners/alba").then().statusCode(200);
    } finally {
      release.countDown();
    }
    assertEquals(4, new HashSet<>(executions.values()).size());
    for (var entry : executions.entrySet()) {
      String owner = entry.getKey();
      var pending = TopologyTest.awaitPending(owner);
      assertEquals(entry.getValue(), pending.get("executionId"));
      given()
          .contentType("application/json")
          .body(
              Map.of(
                  "ownerId",
                  owner,
                  "messageId",
                  "reject-" + owner,
                  "interactionId",
                  pending.get("id"),
                  "choice",
                  "reject",
                  "text",
                  ""))
          .post("/api/messages")
          .then()
          .statusCode(200);
      TopologyTest.awaitStatus(entry.getValue(), "SUCCEEDED");
    }
    synchronized (store) {
      assertEquals(
          4,
          store.snapshot().events.stream()
              .filter(event -> event.type().equals("ROOT_STARTED"))
              .count());
      assertTrue(store.snapshot().matches.isEmpty());
      assertTrue(
          store.snapshot().players.values().stream().allMatch(player -> player.version == 1));
    }
  }

  @Test
  void simultaneousFindRequestsKeepTheirNestedNegotiationsAndReviewsIsolated() throws Exception {
    store.reset(false);
    var match = new DemoSession.Match();
    match.id = "concurrent-find-match";
    match.organizer = "alba";
    match.hour = 19;
    match.style = "competitive";
    match.area = "Barcelona";
    match.date = "Synthetic Thursday";
    match.level = "intermediate";
    match.players.put("alba", "left");
    store.snapshot().matches.put(match.id, match);
    var executions = new LinkedHashMap<String, String>();
    try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
      var futures = new LinkedHashMap<String, Future<String>>();
      for (String owner : List.of("nico", "luz", "teo"))
        futures.put(
            owner,
            pool.submit(
                () ->
                    given()
                        .contentType("application/json")
                        .body(
                            Map.of(
                                "ownerId",
                                owner,
                                "messageId",
                                UUID.randomUUID().toString(),
                                "text",
                                "Find a competitive match"))
                        .post("/api/messages")
                        .then()
                        .statusCode(200)
                        .extract()
                        .path("executionId")));
      for (var entry : futures.entrySet())
        executions.put(entry.getKey(), entry.getValue().get(10, TimeUnit.SECONDS));
    }
    var interactions = new HashSet<String>();
    for (var entry : executions.entrySet()) {
      var pending = TopologyTest.awaitPending(entry.getKey());
      assertEquals(entry.getValue(), pending.get("executionId"));
      assertTrue(interactions.add((String) pending.get("id")));
      given()
          .contentType("application/json")
          .body(
              Map.of(
                  "ownerId",
                  entry.getKey(),
                  "messageId",
                  UUID.randomUUID().toString(),
                  "interactionId",
                  pending.get("id"),
                  "choice",
                  "reject",
                  "text",
                  ""))
          .post("/api/messages")
          .then()
          .statusCode(200);
      TopologyTest.awaitStatus(entry.getValue(), "SUCCEEDED");
    }
    assertEquals(1, store.snapshot().matches.get(match.id).players.size());
  }

  private String send(String owner) {
    return given()
        .contentType("application/json")
        .body(
            Map.of(
                "ownerId",
                owner,
                "messageId",
                "shared-channel-message-id",
                "text",
                "My level is intermediate"))
        .post("/api/messages")
        .then()
        .statusCode(200)
        .extract()
        .path("executionId");
  }
}
