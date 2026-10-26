package dev.padelpot;

import dev.padelpot.domain.OwnerAction;
import dev.padelpot.domain.OwnerChatPath;
import dev.padelpot.domain.OwnerReply;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.pipelineframework.blocks.chat.ChatHandoff;
import org.pipelineframework.blocks.chat.ChatProposal;
import org.pipelineframework.blocks.chat.ChatResult;
import org.pipelineframework.blocks.chat.ChatState;
import org.pipelineframework.config.pipeline.PipelineJson;
import org.pipelineframework.service.ReactiveService;

/** Accepts exactly one owner-scoped Block result and exposes a typed application branch. */
@ApplicationScoped
public class ProjectOwnerChat implements ReactiveService<ChatResult, OwnerChatPath> {
  @Override
  public Uni<OwnerChatPath> process(ChatResult result) {
    return Uni.createFrom().item(() -> project(result));
  }

  static OwnerChatPath project(ChatResult result) {
    if (result.reply().isPresent()) {
      var reply = result.reply().orElseThrow();
      var scope = reply.proposedNextState().scope();
      if (!"padelpot-demo".equals(scope.tenantId())
          || !scope.ownerId().equals(scope.conversationId())
          || !reply.messageId().startsWith(scope.ownerId() + ":"))
        throw new IllegalArgumentException("Chat reply scope does not match the authorised owner");
      return new OwnerChatPath.Reply(new OwnerReply(
          reply.messageId(), scope.ownerId(), reply.text()));
    }
    ChatHandoff handoff = result.handoff().orElseThrow();
    ChatState state = handoff.state();
    ChatProposal proposal = handoff.proposal();
    if (!"padelpot-demo".equals(state.scope().tenantId())
        || !state.scope().ownerId().equals(state.scope().conversationId())
        || !state.messageId().startsWith(state.scope().ownerId() + ":")
        || !"application".equals(proposal.binding())
        || !"owner.action.apply".equals(proposal.operation())
        || !"COMMAND".equals(proposal.kind())
        || proposal.argumentsJson().getBytes(StandardCharsets.UTF_8).length > 4096) {
      throw new IllegalArgumentException("Chat handoff is outside the authorized owner action contract");
    }
    OwnerAction action;
    try {
      action = PipelineJson.mapper().readValue(proposal.argumentsJson(), OwnerAction.class);
    } catch (Exception failure) {
      throw new IllegalArgumentException("Chat handoff has invalid typed owner arguments", failure);
    }
    if (action.requestId() == null || action.ownerId() == null || action.ownerText() == null
        || action.kind() == null || action.explanation() == null || action.field() == null
        || action.value() == null
        || !state.messageId().equals(action.requestId())
        || !state.scope().ownerId().equals(action.ownerId())
        || !state.message().equals(action.ownerText())
        || !Set.of("open", "find", "profile", "learn").contains(action.kind())
        || action.explanation().length() > 1600
        || action.field().length() > 80
        || action.value().length() > 500
        || ("open".equals(action.kind())
            && ((action.hour() != 19 && action.hour() != 20)
                || !Set.of("social", "competitive").contains(action.style())
                || !Set.of("either", "left", "right").contains(action.side())))) {
      throw new IllegalArgumentException("Chat handoff violates owner action rules");
    }
    return new OwnerChatPath.Action(action);
  }
}
