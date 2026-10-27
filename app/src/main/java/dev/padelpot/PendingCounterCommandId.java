package dev.padelpot;

import dev.padelpot.domain.PendingCounter;
import jakarta.enterprise.context.ApplicationScoped;
import org.pipelineframework.command.CommandDescriptor;
import org.pipelineframework.command.CommandIdGenerator;

@ApplicationScoped
public class PendingCounterCommandId implements CommandIdGenerator<PendingCounter> {
  @Override
  public String commandId(CommandDescriptor descriptor, PendingCounter counter) {
    return "pending-counter:" + counter.requestId();
  }
}
