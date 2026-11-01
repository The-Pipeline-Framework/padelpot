package dev.padelpot;

import dev.padelpot.domain.Review;
import jakarta.enterprise.context.ApplicationScoped;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.pipelineframework.command.CommandDescriptor;
import org.pipelineframework.command.CommandIdGenerator;

/** The approved prompt's business identity also fences its publication Command. */
@ApplicationScoped
public class ReviewPublicationCommandId implements CommandIdGenerator<Review> {
  public String eventId(Review review) {
    String identity = review.state().requestId() + ":" + review.kind() + ":" + review.state().turn()
        + ":" + review.state().actorId() + ":" + review.state().reviewRound() + ":" + review.prompt();
    return "padelpot-demo:" + review.state().requestId() + ":agent:review:"
        + UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8));
  }

  public String commandId(CommandDescriptor descriptor, Review review) { return eventId(review); }
}
