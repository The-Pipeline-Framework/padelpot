package dev.padelpot;

import dev.padelpot.domain.*;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.pipelineframework.service.ReactiveService;

@ApplicationScoped
public class RequestFinalReview implements ReactiveService<Review, Review> {
  public Uni<Review> process(Review r) {
    return Uni.createFrom().item(r);
  }
}
