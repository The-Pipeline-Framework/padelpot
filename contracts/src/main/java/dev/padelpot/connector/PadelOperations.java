package dev.padelpot.connector;

import dev.padelpot.domain.*;
import org.pipelineframework.blocks.chat.ChatState;
import org.pipelineframework.blocks.chat.ChatResult;

public interface PadelOperations {
  ModelInput contextWithHistory(TurnState state);
  ModelInput context(TurnState state);

  ChatState chatContext(ChatIngress ingress);

  CourtGuide courtGuide(CourtGuideRequest request);

  Result acceptChatReply(ChatResult result);

  TurnOutcome apply(ValidatedModelTurn turn);

  OwnerInteraction answer(AnsweredReview answer);

  OwnerInteraction answerFinal(AnsweredReview answer);

  Result eraseHistory(HistoryEraseRequest request);
}
