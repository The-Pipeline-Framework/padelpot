package dev.padelpot;

import dev.padelpot.domain.*;

/** Application adapter binds an admitted owner reply to the native pending review. */
public final class OwnerProjector {
  public AnsweredReview fromText(Review r, String text, String actor) {
    if (text == null) throw new IllegalArgumentException("A reply is required");
    if (r.kind().equals("conversation")) return project(r, new HumanAnswer("clarify", text), actor);
    String trimmed = text.trim();
    String value = trimmed.toLowerCase(java.util.Locale.ROOT);
    String choice;
    if (java.util.Set.of("approve", "yes", "sí", "si").contains(value)) choice = "approve";
    else if (java.util.Set.of("decline", "reject", "no").contains(value)) choice = "reject";
    else if (value.startsWith("counter:") && !trimmed.substring(8).isBlank())
      return project(r, new HumanAnswer("counter", trimmed.substring(8).trim()), actor);
    else if (r.kind().equals("clarify")
        && java.util.Set.of("hour", "style").contains(r.field())
        && (acceptsFlexibility(text) || (!r.value().isBlank() && value.equals(r.value()))))
      choice = "approve";
    else
      throw new IllegalArgumentException(
          "Reply approve, decline, or counter: followed by your suggestion. Your pending question"
              + " remains open.");
    return project(r, new HumanAnswer(choice, text), actor);
  }

  static boolean acceptsFlexibility(String text) {
    return java.util.Set.of("i'm flexible", "i’m flexible", "i am flexible", "flexible")
        .contains(text.trim().toLowerCase(java.util.Locale.ROOT));
  }

  public AnsweredReview project(Review r, HumanAnswer a, String actor) {
    if (!r.state().ownerId().equals(actor))
      throw new IllegalArgumentException("Wrong owner for pending interaction");
    if (a == null
        || a.choice() == null
        || !java.util.Set.of("approve", "reject", "counter", "clarify").contains(a.choice()))
      throw new IllegalArgumentException("Invalid owner choice");
    if (a.text() == null || a.text().length() > 1200)
      throw new IllegalArgumentException("Invalid completion text");
    if (r.kind().equals("conversation")
        && (!java.util.Set.of("clarify", "reject").contains(a.choice())
            || (a.choice().equals("clarify") && a.text().isBlank())))
      throw new IllegalArgumentException(
          "Answer the question or cancel it; this is not an approval");
    return new AnsweredReview(r, a, actor);
  }
}
