package dev.padelpot;

import dev.padelpot.domain.*;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.pipelineframework.service.ReactiveService;

@io.quarkus.arc.Unremovable
@ApplicationScoped
public class UnwrapAgent implements ReactiveService<AgentResult, AgentEnd> {
  public Uni<AgentEnd> process(AgentResult a) {
    return Uni.createFrom().item(a.outcome());
  }
}
