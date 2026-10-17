package dev.padelpot;

import dev.padelpot.domain.*;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Set;
import org.pipelineframework.service.ReactiveService;

@ApplicationScoped
public class ValidateTurn implements ReactiveService<ModelTurn, ValidatedModelTurn> {
  static final Set<String> ACTIONS =
      Set.of(
          "message",
          "open",
          "find",
          "persona_update",
          "learn",
          "propose",
          "counter",
          "accept",
          "decline",
          "ask_owner",
          "request_field",
          "disclose");

  public Uni<ValidatedModelTurn> process(ModelTurn t) {
    return Uni.createFrom().item(validate(t));
  }

  static ValidatedModelTurn validate(ModelTurn t) {
    AgentDecision d = t.decision();
    if (d == null || d.action() == null)
      return new ValidatedModelTurn(
          t.state(),
          new AgentDecision(
              "decline", 0, "", "", "", "", "Invalid proposal; no action authorized."));
    boolean terms = Set.of("open", "propose", "counter", "accept").contains(d.action());
    boolean valid =
        d != null
            && d.action() != null
            && ACTIONS.contains(d.action())
            && d.explanation() != null
            && d.explanation().length() <= 1600
            && d.field() != null
            && d.field().length() <= 80
            && d.value() != null
            && d.value().length() <= 500;
    if (terms)
      valid &=
          d.hour() != null
              && d.style() != null
              && d.side() != null
              && (d.hour() == 19 || d.hour() == 20)
              && Set.of("social", "competitive").contains(d.style())
              && Set.of("either", "left", "right").contains(d.side());
    return valid
        ? new ValidatedModelTurn(t.state(), t.decision())
        : new ValidatedModelTurn(
            t.state(),
            new AgentDecision(
                "decline",
                0,
                "",
                "",
                "",
                "",
                "The model returned an invalid proposal. No action was authorized."));
  }
}
