package dev.padelpot;

import dev.padelpot.domain.Owner;
import dev.padelpot.domain.Result;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.pipelineframework.service.ReactiveService;

@ApplicationScoped
public class PublishOwnerResult implements ReactiveService<Owner, Result> {
  public Uni<Result> process(Owner value) {
    var receipt = value.change();
    return Uni.createFrom()
        .item(
            new Result(
                receipt.requestId(), receipt.ownerId(), receipt.status(), receipt.message(), ""));
  }
}
