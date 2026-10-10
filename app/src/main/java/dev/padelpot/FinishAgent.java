package dev.padelpot;

import dev.padelpot.domain.*;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.pipelineframework.service.ReactiveService;

@io.quarkus.arc.Unremovable
@ApplicationScoped
public class FinishAgent implements ReactiveService<AgentResult, AgentResult> {
  public Uni<AgentResult> process(AgentResult input) {
    return Uni.createFrom().item(input);
  }
}
