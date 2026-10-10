package dev.padelpot.connector;

import dev.padelpot.domain.*;

public interface PadelOperations {
  ModelInput context(TurnState state);

  TurnOutcome apply(ModelTurn turn);

  OwnerInteraction answer(AnsweredReview answer);

  OwnerInteraction answerFinal(AnsweredReview answer);
}
