package dev.padelpot;

import dev.padelpot.domain.OwnerAction;
import jakarta.enterprise.context.ApplicationScoped;
import org.pipelineframework.command.CommandDescriptor;
import org.pipelineframework.command.CommandIdGenerator;

@ApplicationScoped
public class OwnerActionCommandId implements CommandIdGenerator<OwnerAction> {
  @Override
  public String commandId(CommandDescriptor descriptor, OwnerAction action) {
    return "owner-action:" + action.requestId();
  }
}
