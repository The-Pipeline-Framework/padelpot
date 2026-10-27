package dev.padelpot.persistence;

import dev.padelpot.domain.ChatIngress;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import org.pipelineframework.mapper.Mapper;

/** Native persistence-aspect mapping for an owner-authorized ChatAgent root. */
@ApplicationScoped
public class ChatIngressMapper implements Mapper<ChatIngress, ApprovedTurnEntity> {
  @Override
  public ApprovedTurnEntity toExternal(ChatIngress ingress) {
    var entity = new ApprovedTurnEntity();
    entity.eventId = "padelpot-demo:" + ingress.requestId() + ":owner";
    entity.tenantId = "padelpot-demo";
    entity.ownerId = ingress.ownerId();
    entity.conversationId = ingress.ownerId();
    entity.requestId = ingress.requestId();
    entity.speaker = "owner";
    entity.occurredAt = Instant.now();
    entity.text = ingress.text();
    entity.status = "CHAT_INPUT";
    entity.matchId = "";
    entity.pendingInteractionId = ingress.pendingInteractionId();
    return entity;
  }

  @Override
  public ChatIngress fromExternal(ApprovedTurnEntity entity) {
    return new ChatIngress(entity.requestId, entity.ownerId, entity.text,
        entity.pendingInteractionId == null ? "" : entity.pendingInteractionId);
  }
}
