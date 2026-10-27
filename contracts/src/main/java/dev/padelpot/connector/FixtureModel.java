package dev.padelpot.connector;

import com.fasterxml.jackson.databind.*;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.*;
import java.util.concurrent.*;
import org.pipelineframework.connector.*;
import org.pipelineframework.connector.llm.*;

/** Explicit offline invariant-test fixture. Never selected as an inference fallback. */
@ApplicationScoped
public class FixtureModel extends LlmQueryConnectorProvider {
  public FixtureModel() {
    super(ConnectorProviderId.of("padelpot.fixture"));
  }

  protected LlmDecisionClientResolver createClientResolver(
      LlmProviderConfiguration c, ConnectorRuntimeContext x) {
    if (!c.model().equals("deterministic-business-fixture"))
      throw new IllegalArgumentException("Fixture provider is test-only");
    return context ->
        CompletableFuture.completedFuture(
            new LlmDecisionClient() {
              public boolean supportsNativeStructuredOutput() {
                return true;
              }

              public CompletionStage<LlmDecision> decide(LlmTurnRequest r) {
                try {
                  JsonNode n = new ObjectMapper().readTree(r.applicationStateJson());
                  if (n.has("message") && n.has("context")) {
                    String message = n.path("message").asText().toLowerCase(Locale.ROOT);
                    String pendingReview = "";
                    for (JsonNode entry : n.path("context")) {
                      if (entry.path("provenance").asText().equals("pending-interaction"))
                        pendingReview = entry.path("text").asText();
                    }
                    if (!pendingReview.isBlank()
                        && r.tools().stream().anyMatch(tool -> tool.alias().equals("pending_counter"))
                        && message.contains("switch to the right") && !message.contains("lift")) {
                      String interactionId = pendingReview.split("interactionId=", 2)[1].split(";", 2)[0];
                      return CompletableFuture.completedFuture(new LlmDecision(
                          new LlmToolProposal("pending_counter", new ObjectMapper().writeValueAsString(
                              Map.of("interactionId", interactionId,
                                  "counterText", n.path("message").asText())))));
                    }
                    if (r.tools().stream().anyMatch(tool -> tool.alias().equals("owner_action"))) {
                      String kind = message.contains("open") || message.contains("host")
                          || message.contains("create") ? "open"
                          : message.contains("find") || message.contains("join") ? "find"
                          : message.contains("feedback") ? "learn"
                          : message.contains("my level") || message.contains("my preference") ? "profile"
                          : "";
                      if (!kind.isEmpty()) {
                        Map<String, Object> action = new LinkedHashMap<>();
                        action.put("kind", kind);
                        action.put("hour", 19);
                        action.put("style", "competitive");
                        action.put("side", "left");
                        action.put("field", kind.equals("profile") ? "level" : kind.equals("learn") ? "style" : "");
                        action.put("value", kind.equals("profile") ? "intermediate" : kind.equals("learn") ? "social" : "");
                        action.put("explanation", "Fixture owner action; approval remains with the owner.");
                        return CompletableFuture.completedFuture(new LlmDecision(
                            new LlmToolProposal("owner_action", new ObjectMapper().writeValueAsString(action))));
                      }
                    }
                    if (r.tools().stream().anyMatch(tool -> tool.alias().equals("court_guide"))
                        && message.contains("court")) {
                      return CompletableFuture.completedFuture(new LlmDecision(
                          new LlmToolProposal("court_guide", "{\"topic\":\"court\"}")));
                    }
                    String answer = message.contains("lift")
                        ? "Left is the proposed side, but I cannot arrange a lift. Your participation"
                            + " still needs explicit approval."
                        : message.contains("joke")
                        ? "I can help with padel and match questions, not unrelated requests."
                        : message.contains("match")
                        ? "Would you like me to open a new match or find one to join?"
                        : "I can help with padel questions and match requests.";
                    for (JsonNode entry : n.path("context")) {
                      if (entry.path("provenance").asText().startsWith("query:")) {
                        answer = "The court guide says " + entry.path("text").asText();
                      }
                    }
                    if (n.path("message").asText().equalsIgnoreCase("What did I say before?")) {
                      for (JsonNode entry : n.path("context")) {
                        if (entry.path("provenance").asText().equals("owner")
                            || entry.path("provenance").asText().equals("user")) {
                          answer = "Earlier you said " + entry.path("text").asText();
                        }
                      }
                    }
                    return CompletableFuture.completedFuture(new LlmDecision(
                        new LlmToolProposal("complete", new ObjectMapper().writeValueAsString(Map.of("text", answer)))));
                  }
                  String task = n.path("task").asText();
                  String field = "", value = "";
                  int hour = 19;
                  String side = "either", style = "competitive";
                  if (task.startsWith("CHAT"))
                    throw new IllegalStateException("Initial owner intent must use ChatAgent");
                  String action = task.contains("Last peer action=propose")
                          || task.contains("Last peer action=counter") ? "accept" : "propose";
                  String brief = n.path("playerBrief").asText();
                  side =
                      (brief.contains("side=left") || brief.contains("side=strong_left"))
                          ? "left"
                          : (brief.contains("side=right") || brief.contains("side=strong_right"))
                              ? "right"
                              : "either";
                  if (task.contains("as organizer")) {
                    String terms = n.path("publicTerms").asText();
                    side = terms.substring(terms.indexOf("side=") + 5).split(";")[0];
                  }
                  String explanation =
                      "The permitted terms fit this player. Participation still needs owner approval.";
                  Map<String, Object> d = new LinkedHashMap<>();
                  d.put("action", action);
                  d.put("hour", hour);
                  d.put("style", style);
                  d.put("side", side);
                  d.put("field", field);
                  d.put("value", value);
                  d.put("explanation", explanation);
                  return CompletableFuture.completedFuture(
                      new LlmDecision(
                          new LlmToolProposal(
                              "complete", new ObjectMapper().writeValueAsString(d))));
                } catch (Exception e) {
                  return CompletableFuture.failedFuture(e);
                }
              }
            });
  }
}
