package dev.padelpot;

import dev.padelpot.domain.*;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.pipelineframework.service.ReactiveService;

@io.quarkus.arc.Unremovable
@ApplicationScoped
public class FinishReview implements ReactiveService<Review, AgentResult> {
  public Uni<AgentResult> process(Review input) {
    return Uni.createFrom().item(new AgentResult(new AgentEnd.ReviewRequired(input)));
  }
}
