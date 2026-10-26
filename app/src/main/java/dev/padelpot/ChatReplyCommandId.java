package dev.padelpot;

import jakarta.enterprise.context.ApplicationScoped;
import dev.padelpot.domain.OwnerReply;
import org.pipelineframework.command.CommandDescriptor;
import org.pipelineframework.command.CommandIdGenerator;

@ApplicationScoped
public class ChatReplyCommandId implements CommandIdGenerator<OwnerReply> {
  @Override
  public String commandId(CommandDescriptor descriptor, OwnerReply reply) {
    return "chat-reply:" + reply.messageId();
  }
}
