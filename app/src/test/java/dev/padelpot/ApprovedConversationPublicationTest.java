package dev.padelpot;

import dev.padelpot.domain.Review;
import dev.padelpot.domain.TurnState;
import dev.padelpot.persistence.ReviewTurnMapper;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class ApprovedConversationPublicationTest {
  private final long deadline = System.currentTimeMillis() + 900_000;
  @Inject DemoSession session;
  @Inject ReviewTurnMapper mapping;

  @Test
  void mappingPreservesFinalReviewAuthorityAndDistinctEventIdentities() {
    session.reset(false);
    var state = state(1);
    var review = new Review(state, "conversation", "May I publish these terms?", "", "");
    assertEquals(review, mapping.fromExternal(mapping.toExternal(review)));
    assertNotEquals(mapping.toExternal(new Review(state(0), review.kind(), review.prompt(), "", "")).eventId,
        mapping.toExternal(review).eventId, "A final review is a separate publication even when its prompt repeats");
    var descriptor = org.mockito.Mockito.mock(org.pipelineframework.command.CommandDescriptor.class);
    var answers = new AnswerCommandId();
    var answer = new dev.padelpot.domain.HumanAnswer("approve", "");
    var first = new dev.padelpot.domain.AnsweredReview(new Review(state(0), review.kind(), review.prompt(), "", ""), answer, "alba");
    var last = new dev.padelpot.domain.AnsweredReview(review, answer, "alba");
    assertNotEquals(answers.commandId(descriptor, first), answers.commandId(descriptor, last),
        "Applying the final answer has its own logical effect identity");
  }

  private TurnState state(int round) {
    var player = session.player("alba");
    return new TurnState("publication-review", "alba", "alba", "chat", "", 1,
        deadline, player.version, 0L, 19, "competitive", "left", "",
        List.of(), "Private owner request", "", round);
  }

}
