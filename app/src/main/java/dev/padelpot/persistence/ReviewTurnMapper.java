package dev.padelpot.persistence;

import dev.padelpot.domain.Review;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Instant;
import org.pipelineframework.mapper.Mapper;

/** A policy-approved prompt appears in history before its correlated Await reply. */
@ApplicationScoped
public class ReviewTurnMapper implements Mapper<Review, ApprovedTurnEntity> {
  @Inject OwnerTurnMapper states;
  @Inject dev.padelpot.ReviewPublicationCommandId identity;

  public ApprovedTurnEntity toExternal(Review review) {
    var entity = states.toExternal(review.state());
    entity.eventId = identity.eventId(review);
    entity.speaker = "agent";
    entity.occurredAt = Instant.now();
    entity.text = review.prompt();
    entity.reviewKind = review.kind();
    entity.reviewField = review.field();
    entity.reviewValue = review.value();
    return entity;
  }

  public Review fromExternal(ApprovedTurnEntity entity) {
    return new Review(
        states.fromExternal(entity), entity.reviewKind, entity.text,
        entity.reviewField, entity.reviewValue);
  }
}
