package dev.padelpot.persistence;

import dev.padelpot.domain.TurnState;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import org.pipelineframework.mapper.Mapper;

/** Only the explicitly selected root admission step uses this mapping. */
@ApplicationScoped
public class OwnerTurnMapper implements Mapper<TurnState, ApprovedTurnEntity> {
  public ApprovedTurnEntity toExternal(TurnState turn) {
    var entity = new ApprovedTurnEntity();
    entity.eventId = "padelpot-demo:" + turn.requestId() + ":owner";
    entity.tenantId = "padelpot-demo";
    entity.ownerId = turn.actorId();
    entity.conversationId = turn.actorId();
    entity.rootOwnerId = turn.ownerId();
    entity.requestId = turn.requestId();
    entity.speaker = "owner";
    entity.occurredAt = Instant.ofEpochMilli(turn.deadline() - 900_000);
    entity.text = turn.ownerText();
    entity.ownerText = turn.ownerText();
    entity.actorId = turn.actorId();
    entity.mode = turn.mode();
    entity.matchId = turn.matchId();
    entity.turn = turn.turn();
    entity.deadline = turn.deadline();
    entity.ownerVersion = turn.ownerVersion();
    entity.organizerVersion = turn.organizerVersion();
    entity.hour = turn.hour();
    entity.style = turn.style();
    entity.side = turn.side();
    entity.lastAction = turn.lastAction();
    entity.transcript.addAll(turn.transcript());
    entity.team = turn.team();
    return entity;
  }

  public TurnState fromExternal(ApprovedTurnEntity entity) {
    return new TurnState(
        entity.requestId, entity.rootOwnerId == null ? entity.ownerId : entity.rootOwnerId,
        entity.actorId, entity.mode, entity.matchId,
        entity.turn, entity.deadline, entity.ownerVersion, entity.organizerVersion,
        entity.hour, entity.style, entity.side, entity.lastAction,
        entity.transcript, entity.ownerText, entity.team);
  }
}
