package dev.padelpot;

import dev.padelpot.domain.*;
import jakarta.enterprise.context.ApplicationScoped;
import org.pipelineframework.command.*;

@ApplicationScoped
public class TurnCommandId implements CommandIdGenerator<ModelTurn> {
  public String commandId(CommandDescriptor d, ModelTurn t) {
    return "turn:" + t.state().requestId() + ":" + t.state().turn();
  }
}
