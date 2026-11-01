package dev.padelpot.persistence;

import dev.padelpot.domain.Result;
import dev.padelpot.domain.Review;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.concurrent.CompletionStage;
import org.pipelineframework.plugin.persistence.PersistenceService;

/** Publishes policy-approved dialogue through the configured TPF persistence shell. */
@ApplicationScoped
public class ApprovedConversationPublisher extends PersistenceService<ApprovedTurnEntity> {
  @Inject ReviewTurnMapper reviews;
  @Inject AgentTurnMapper results;

  public CompletionStage<Review> publishReview(Review review) {
    return process(reviews.toExternal(review)).replaceWith(review).subscribeAsCompletionStage();
  }

  public CompletionStage<Result> publishResult(Result result) {
    return process(results.toExternal(result)).replaceWith(result).subscribeAsCompletionStage();
  }
}
