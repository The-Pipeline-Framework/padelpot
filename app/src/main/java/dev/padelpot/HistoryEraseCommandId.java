package dev.padelpot;

import dev.padelpot.domain.HistoryEraseRequest;
import jakarta.enterprise.context.ApplicationScoped;
import org.pipelineframework.command.*;

@ApplicationScoped
public class HistoryEraseCommandId implements CommandIdGenerator<HistoryEraseRequest> {
  public String commandId(CommandDescriptor descriptor, HistoryEraseRequest request) {
    return "history-erase:" + request.tenantId() + ":" + request.ownerId() + ":" + request.requestId();
  }
}
