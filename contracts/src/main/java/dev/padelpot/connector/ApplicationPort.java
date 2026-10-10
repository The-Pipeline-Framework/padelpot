package dev.padelpot.connector;

import dev.padelpot.domain.*;

public interface ApplicationPort {
  ModelInput context(TurnState state);

  TurnOutcome apply(ModelTurn turn);

  TurnOutcome answer(AnsweredReview answer);

  Result answerFinal(AnsweredReview answer);
}
