package dev.padelpot.connector;

import dev.padelpot.domain.*;

public interface PadelOperations {
  ModelInput contextWithHistory(TurnState state);
  ModelInput context(TurnState state);

  TurnOutcome apply(ValidatedModelTurn turn);

  OwnerInteraction answer(AnsweredReview answer);

  OwnerInteraction answerFinal(AnsweredReview answer);

  Result eraseHistory(HistoryEraseRequest request);
}
