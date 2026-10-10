package dev.padelpot;

import dev.padelpot.domain.*;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.pipelineframework.service.ReactiveService;

@io.quarkus.arc.Unremovable
@ApplicationScoped
public class FinishResult implements ReactiveService<Result, Result> {
  public Uni<Result> process(Result r) {
    return Uni.createFrom().item(r);
  }
}
