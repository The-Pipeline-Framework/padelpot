package dev.padelpot;

import dev.padelpot.domain.*;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.pipelineframework.service.ReactiveService;

@ApplicationScoped
public class FinishOutcome implements ReactiveService<Object, AgentResult> {
  public Uni<AgentResult> process(Object v) {
    return Uni.createFrom()
        .item(
            v instanceof AgentResult a
                ? a
                : new AgentResult(
                    v instanceof Review r
                        ? new AgentEnd.ReviewRequired(r)
                        : new AgentEnd.Finished((Result) v)));
  }
}
