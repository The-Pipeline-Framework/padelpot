package dev.padelpot;

import dev.padelpot.domain.*;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.pipelineframework.service.ReactiveService;

@ApplicationScoped
public class StopFurtherNegotiation implements ReactiveService<TurnState, Result> {
  public Uni<Result> process(TurnState state) {
    return Uni.createFrom()
        .item(
            new Result(
                state.requestId(),
                state.ownerId(),
                "NEEDS_FRESH_REQUEST",
                "This request reached its final review. Send a fresh request to negotiate new"
                    + " boundaries.",
                state.matchId()));
  }
}
