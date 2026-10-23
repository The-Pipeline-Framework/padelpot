package dev.padelpot;

import jakarta.enterprise.context.ApplicationScoped;
import org.pipelineframework.blocks.chat.ChatResult;
import org.pipelineframework.command.CommandDescriptor;
import org.pipelineframework.command.CommandIdGenerator;

@ApplicationScoped
public class ChatReplyCommandId implements CommandIdGenerator<ChatResult> {
  @Override
  public String commandId(CommandDescriptor descriptor, ChatResult result) {
    return "chat-reply:" + result.reply().orElseThrow(() ->
        new IllegalStateException("ChatAgent handoff cannot use reply authority")).messageId();
  }
}
