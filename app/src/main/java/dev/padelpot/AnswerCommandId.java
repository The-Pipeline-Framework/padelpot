package dev.padelpot;

import dev.padelpot.domain.*;
import jakarta.enterprise.context.ApplicationScoped;
import org.pipelineframework.command.*;

@ApplicationScoped
public class AnswerCommandId implements CommandIdGenerator<AnsweredReview> {
  public String commandId(CommandDescriptor d, AnsweredReview t) {
    return "human:"
        + t.review().state().requestId()
        + ":"
        + t.review().state().turn()
        + ":"
        + t.review().kind();
  }
}
