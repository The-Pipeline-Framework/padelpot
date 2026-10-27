package dev.padelpot.connector;

import dev.padelpot.domain.*;
import java.util.concurrent.CompletionStage;
import org.pipelineframework.blocks.chat.ChatState;

public interface PadelOperations {
  ModelInput contextWithHistory(TurnState state);
  ModelInput context(TurnState state);

  ChatState chatContext(ChatIngress ingress);

  CourtGuide courtGuide(CourtGuideRequest request);

  Result acceptChatReply(OwnerReply reply);

  TurnOutcome applyOwnerAction(OwnerAction action);

  TurnOutcome apply(ValidatedModelTurn turn);

  OwnerInteraction answer(AnsweredReview answer);

  OwnerInteraction answerFinal(AnsweredReview answer);

  Result eraseHistory(HistoryEraseRequest request);
}
