package dev.padelpot;

import dev.padelpot.domain.Result;
import jakarta.enterprise.context.ApplicationScoped;
import org.pipelineframework.command.CommandDescriptor;
import org.pipelineframework.command.CommandIdGenerator;

@ApplicationScoped
public class ResultPublicationCommandId implements CommandIdGenerator<Result> {
  public String eventId(Result result) { return "padelpot-demo:" + result.requestId() + ":agent"; }
  public String commandId(CommandDescriptor descriptor, Result result) { return eventId(result); }
}
