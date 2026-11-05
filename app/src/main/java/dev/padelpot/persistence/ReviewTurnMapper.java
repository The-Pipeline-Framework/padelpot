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

  public ApprovedTurnEntity toExternal(Review review) {
    var entity = states.toExternal(review.state());
    String reviewPhase = review.state().reviewRound() == 0 ? "" : review.state().reviewRound() + ":";
    String identity = review.state().requestId() + ":" + review.kind() + ":" + review.state().turn()
        + ":" + review.state().actorId() + ":" + reviewPhase + review.prompt();
    entity.eventId = "padelpot-demo:" + review.state().requestId() + ":agent:review:"
        + java.util.UUID.nameUUIDFromBytes(identity.getBytes(java.nio.charset.StandardCharsets.UTF_8));
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
