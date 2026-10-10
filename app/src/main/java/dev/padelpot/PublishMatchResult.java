package dev.padelpot;

import dev.padelpot.domain.Match;
import dev.padelpot.domain.Result;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.pipelineframework.service.ReactiveService;

@ApplicationScoped
public class PublishMatchResult implements ReactiveService<Match, Result> {
  public Uni<Result> process(Match value) {
    var receipt = value.change();
    return Uni.createFrom()
        .item(
            new Result(
                receipt.requestId(),
                receipt.ownerId(),
                receipt.status(),
                receipt.message(),
                value.id()));
  }
}
