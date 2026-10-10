package dev.padelpot;

import dev.padelpot.domain.*;

/** Application adapter binds an admitted owner reply to the native pending review. */
public final class OwnerProjector {
  public AnsweredReview project(Review r, HumanAnswer a, String actor) {
    if (!r.state().ownerId().equals(actor))
      throw new IllegalArgumentException("Wrong owner for pending interaction");
    if (a == null
        || a.choice() == null
        || !java.util.Set.of("approve", "reject", "counter", "clarify").contains(a.choice()))
      throw new IllegalArgumentException("Invalid owner choice");
    if (a.text() == null || a.text().length() > 1200)
      throw new IllegalArgumentException("Invalid completion text");
    return new AnsweredReview(r, a, actor);
  }
}
