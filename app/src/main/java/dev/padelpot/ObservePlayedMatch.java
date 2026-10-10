package dev.padelpot;

import dev.padelpot.domain.*;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.pipelineframework.service.ReactiveService;

/** One authorised observation; TPF persists its mapped aggregate before acknowledgement. */
@ApplicationScoped
public class ObservePlayedMatch implements ReactiveService<PlayObservation, Result> {
  public Uni<Result> process(PlayObservation observation) {
    var receipt = observation.match().change();
    return Uni.createFrom()
        .item(
            new Result(
                receipt.requestId(),
                receipt.ownerId(),
                receipt.status(),
                receipt.message(),
                observation.match().id()));
  }
}
