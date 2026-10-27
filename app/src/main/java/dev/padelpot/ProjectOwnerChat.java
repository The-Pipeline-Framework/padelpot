package dev.padelpot;

import dev.padelpot.domain.OwnerAction;
import dev.padelpot.domain.OwnerChatPath;
import dev.padelpot.domain.OwnerReply;
import dev.padelpot.domain.PendingCounter;
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
      boolean pendingReview = reply.proposedNextState().evidence().stream()
          .anyMatch(evidence -> evidence.provenance().equals("pending-interaction"));
      String text = pendingReview
          ? reply.text() + " The court side and other proposed terms remain as shown in the"
              + " pending offer; nothing has been approved or changed."
          : reply.text();
      return new OwnerChatPath.Reply(new OwnerReply(reply.messageId(), scope.ownerId(), text));
    }
    ChatHandoff handoff = result.handoff().orElseThrow();
    ChatState state = handoff.state();
    ChatProposal proposal = handoff.proposal();
    if (!"padelpot-demo".equals(state.scope().tenantId())
        || !state.scope().ownerId().equals(state.scope().conversationId())
        || !state.messageId().startsWith(state.scope().ownerId() + ":")
        || !"application".equals(proposal.binding())
        || !"COMMAND".equals(proposal.kind())
        || proposal.argumentsJson().getBytes(StandardCharsets.UTF_8).length > 4096) {
      throw new IllegalArgumentException("Chat handoff is outside the authorized owner action contract");
    }
    if ("review.counter.submit".equals(proposal.operation())) {
      PendingCounter counter;
      try {
        counter = PipelineJson.mapper().readValue(proposal.argumentsJson(), PendingCounter.class);
      } catch (Exception failure) {
        throw new IllegalArgumentException("Chat handoff has invalid typed counter arguments", failure);
      }
      if (counter.requestId() == null || counter.ownerId() == null
          || counter.ownerText() == null || counter.interactionId() == null
          || counter.counterText() == null
          || !state.messageId().equals(counter.requestId())
          || !state.scope().ownerId().equals(counter.ownerId())
          || !state.message().equals(counter.ownerText())
          || counter.counterText().isBlank() || counter.counterText().length() > 1200
          || !state.message().toLowerCase(java.util.Locale.ROOT).contains(
              counter.counterText().trim().toLowerCase(java.util.Locale.ROOT))
          || state.evidence().stream().noneMatch(evidence ->
              evidence.provenance().equals("pending-interaction")
                  && evidence.entryId().equals("pending-review:" + counter.interactionId()))) {
        throw new IllegalArgumentException("Chat handoff violates pending counter rules");
      }
      return new OwnerChatPath.Counter(counter);
    }
    if (!"owner.action.apply".equals(proposal.operation()))
      throw new IllegalArgumentException("Chat handoff uses an unknown application operation");
    if (state.evidence().stream().anyMatch(evidence ->
        evidence.provenance().equals("pending-interaction")))
      return new OwnerChatPath.Reply(new OwnerReply(state.messageId(), state.scope().ownerId(),
          "You have a pending decision. Please finish or decline it before starting another"
              + " match request; I can answer questions without changing that decision."));
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
