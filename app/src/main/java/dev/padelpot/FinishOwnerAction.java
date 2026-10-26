package dev.padelpot;

import dev.padelpot.domain.AgentEnd;
import dev.padelpot.domain.TurnOutcome;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.pipelineframework.service.ReactiveService;

@ApplicationScoped
public class FinishOwnerAction implements ReactiveService<TurnOutcome, AgentEnd> {
  @Override
  public Uni<AgentEnd> process(TurnOutcome outcome) {
    if (outcome instanceof TurnOutcome.ReviewRequired review) {
      return Uni.createFrom().item(new AgentEnd.ReviewRequired(review.value()));
    }
    if (outcome instanceof TurnOutcome.Finished finished) {
      return Uni.createFrom().item(new AgentEnd.Finished(finished.value()));
    }
    throw new IllegalArgumentException("Negotiation continuation must enter the player agent");
  }
}
