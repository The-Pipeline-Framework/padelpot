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
                  String task = n.path("task").asText(), text = n.path("ownerMessage").asText();
                  String action = "message",
                      field = "",
                      value = "",
                      explanation =
                          "Tell me your approximate level, broad area and typical availability.";
                  int hour = 19;
                  String side = "either", style = "competitive";
                  if (task.startsWith("CHAT")) {
                    if (text.toLowerCase().contains("open")) {
                      action = "open";
                      side = "left";
                    } else if (text.toLowerCase().contains("find")) action = "find";
                    else if (text.toLowerCase().contains("feedback")) {
                      action = "learn";
                      field = "style";
                      value = "social";
                      explanation =
                          "You enjoyed the friendly pace. Would you prefer social matches?";
                    } else if (text.toLowerCase().contains("level")) {
                      action = "persona_update";
                      field = "level";
                      value = "intermediate";
                    }
                  } else {
                    action =
                        task.contains("Last peer action=propose")
                                || task.contains("Last peer action=counter")
                            ? "accept"
                            : "propose";
                    String brief = n.path("playerBrief").asText();
                    side =
                        brief.contains("side=left")
                            ? "left"
                            : brief.contains("side=right") ? "right" : "either";
                    if (task.contains("as organizer")) {
                      String terms = n.path("publicTerms").asText();
                      side = terms.substring(terms.indexOf("side=") + 5).split(";")[0];
                    }
                    explanation =
                        "The permitted terms fit this player. Participation still needs owner"
                            + " approval.";
                  }
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
