package dev.padelpot;

import static org.junit.jupiter.api.Assertions.*;

import dev.padelpot.domain.OwnerAction;
import dev.padelpot.domain.OwnerChatPath;
import dev.padelpot.domain.Review;
import dev.padelpot.domain.TurnOutcome;
import dev.padelpot.domain.AnsweredReview;
import dev.padelpot.domain.HumanAnswer;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.pipelineframework.blocks.chat.ChatEvidence;
import org.pipelineframework.blocks.chat.ChatHandoff;
import org.pipelineframework.blocks.chat.ChatProposal;
import org.pipelineframework.blocks.chat.ChatResult;
import org.pipelineframework.blocks.chat.ChatState;
import org.pipelineframework.config.pipeline.PipelineJson;
import org.pipelineframework.connector.conversation.ConversationScope;

class OwnerActionTest {
  private DemoSession session;
  private PadelApplication application;

  @BeforeEach
  void setup() {
    session = new DemoSession();
    session.reset(false);
    application = new PadelApplication();
    application.store = session;
  }

  private static ChatState state(String messageId, String message) {
    return ChatState.start(new ConversationScope("padelpot-demo", "alba", "alba"),
        messageId, message);
  }

  private static ChatResult handoff(ChatState state, OwnerAction action) throws Exception {
    return ChatResult.handedOff(new ChatHandoff(state,
        new ChatProposal("application", "owner.action.apply",
            PipelineJson.mapper().writeValueAsString(action), "COMMAND")));
  }

  @Test
  void typedOpenProposalStillRequiresOwnerApproval() throws Exception {
    ChatState state = state("alba:open-1", "Open a competitive match at 19:00");
    OwnerAction proposal = new OwnerAction(state.messageId(), "alba", state.message(),
        "open", 19, "competitive", "left", "", "", "Owner requested a match.");

    OwnerAction accepted = assertInstanceOf(OwnerChatPath.Action.class,
        ProjectOwnerChat.project(handoff(state, proposal))).value();
    Review review = assertInstanceOf(TurnOutcome.ReviewRequired.class,
        application.applyOwnerAction(accepted)).value();

    assertEquals("open", review.kind());
    assertTrue(review.prompt().contains("approve"));
    assertTrue(session.snapshot().matches.isEmpty());
    application.answer(new AnsweredReview(review, new HumanAnswer("approve", ""), "alba"));
    assertFalse(session.snapshot().matches.isEmpty());
  }

  @Test
  void followUpCorrectionUsesTheCurrentMessageAndCarriedHistory() throws Exception {
    ChatState state = state("alba:correction-2", "Actually, find one instead")
        .appendEvidence(new ChatEvidence("previous", "user", "Open a match", Optional.empty()));
    OwnerAction proposal = new OwnerAction(state.messageId(), "alba", state.message(),
        "find", 0, "", "", "", "", "Owner corrected the request.");

    OwnerAction accepted = assertInstanceOf(OwnerChatPath.Action.class,
        ProjectOwnerChat.project(handoff(state, proposal))).value();
    assertEquals("find", accepted.kind());
    assertInstanceOf(TurnOutcome.Finished.class, application.applyOwnerAction(accepted));
    assertTrue(session.snapshot().matches.isEmpty());
  }

  @Test
  void rejectsForgedScopeAndOperationBeforeBusinessCommand() throws Exception {
    ChatState state = state("alba:action-3", "Open a match");
    OwnerAction foreign = new OwnerAction(state.messageId(), "nico", state.message(),
        "open", 19, "competitive", "left", "", "", "forged");
    assertThrows(IllegalArgumentException.class,
        () -> ProjectOwnerChat.project(handoff(state, foreign)));
    assertThrows(IllegalArgumentException.class, () -> ProjectOwnerChat.project(
        ChatResult.handedOff(new ChatHandoff(state, new ChatProposal(
            "application", "turn.apply", "{}", "COMMAND")))));
    assertTrue(session.snapshot().intents.isEmpty());
  }

  @Test
  void ownerActionCannotStartAnotherMatchDuringPendingReview() throws Exception {
    ChatState state = state("alba:counter-1", "Could we switch to the right instead?")
        .appendEvidence(new ChatEvidence("pending-review:review-1", "pending-interaction",
            "Current live review: interactionId=review-1", Optional.empty()));
    assertThrows(IllegalArgumentException.class, () -> ProjectOwnerChat.project(
        ChatResult.handedOff(new ChatHandoff(state, new ChatProposal(
            "application", "unknown.command",
            "{}", "COMMAND")))));
    OwnerAction other = new OwnerAction(state.messageId(), "alba", state.message(),
        "find", 0, "", "", "", "", "Start another search");
    assertInstanceOf(OwnerChatPath.Reply.class,
        ProjectOwnerChat.project(handoff(state, other)));
  }
}
