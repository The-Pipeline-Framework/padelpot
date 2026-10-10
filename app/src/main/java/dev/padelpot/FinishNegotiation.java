package dev.padelpot;

import dev.padelpot.domain.*;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.pipelineframework.service.ReactiveService;

@io.quarkus.arc.Unremovable
@ApplicationScoped
public class FinishNegotiation implements ReactiveService<Result, AgentResult> {
  public Uni<AgentResult> process(Result input) {
    return Uni.createFrom().item(new AgentResult(new AgentEnd.Finished(input)));
  }
}
