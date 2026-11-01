package dev.padelpot.persistence;

import dev.padelpot.domain.Result;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import org.pipelineframework.mapper.Mapper;

/** The terminal policy result is an approved agent utterance; a model proposal is not. */
@ApplicationScoped
public class AgentTurnMapper implements Mapper<Result, ApprovedTurnEntity> {
  public ApprovedTurnEntity toExternal(Result result) {
    var entity = new ApprovedTurnEntity();
    entity.eventId = "padelpot-demo:" + result.requestId() + ":agent";
    entity.tenantId = "padelpot-demo";
    entity.ownerId = result.ownerId();
    entity.conversationId = result.ownerId();
    entity.requestId = result.requestId();
    entity.speaker = "agent";
    entity.occurredAt = Instant.now();
    entity.text = result.message();
    entity.status = result.status();
    entity.matchId = result.matchId();
    return entity;
  }

  public Result fromExternal(ApprovedTurnEntity entity) {
    return new Result(entity.requestId, entity.ownerId, entity.status, entity.text, entity.matchId);
  }
}
