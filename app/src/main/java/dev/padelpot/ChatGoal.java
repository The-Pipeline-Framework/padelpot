package dev.padelpot;

import java.util.Set;
import java.util.regex.Pattern;

/** Protect explicit owner requests; leave other conversational language to the model. */
enum ChatGoal {
  OPEN,
  FIND,
  UNSPECIFIED;

  private static final String PREFIX =
      "(?:(?:please|can you|could you)\\s+|(?:i want to|i'd like to|i would like to)\\s+)?";
  private static final Pattern OPEN_REQUEST =
      Pattern.compile(
          "^" + PREFIX + "(?:open|create|host|organize|organise)\\b[^\\n.!?]*\\bmatch\\b",
          Pattern.CASE_INSENSITIVE);
  private static final Pattern FIND_REQUEST =
      Pattern.compile(
          "^" + PREFIX + "(?:find|join|search for|look for)\\b[^\\n.!?]*\\bmatch\\b",
          Pattern.CASE_INSENSITIVE);
  private static final Pattern RECONSIDER =
      Pattern.compile(
          "\\b(?:instead|rather than|don't|do not|cancel)\\b", Pattern.CASE_INSENSITIVE);

  static ChatGoal from(String ownerText) {
    // A resumed conversation appends the question and answer after the original request.
    String request = ownerText.strip().split("\\n", 2)[0];
    if (RECONSIDER.matcher(request).find()) return UNSPECIFIED;
    if (OPEN_REQUEST.matcher(request).find()) return OPEN;
    if (FIND_REQUEST.matcher(request).find()) return FIND;
    return UNSPECIFIED;
  }

  boolean accepts(String action) {
    return this == UNSPECIFIED
        || Set.of("message", "ask_owner", "decline").contains(action)
        || (this == OPEN && action.equals("open"))
        || (this == FIND && action.equals("find"));
  }
}
