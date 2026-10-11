package dev.padelpot;

import dev.padelpot.domain.*;
import java.util.*;

/** Deterministic rules; no model-authored prose enters the peer wire contract. */
public final class Policy {
  private Policy() {}

  public static final Set<String> PUBLIC_FIELDS = Set.of("level", "area", "style", "side");
  public static final Set<String> EDITABLE =
      Set.of(
          "level",
          "area",
          "availability",
          "preferredHour",
          "style",
          "side",
          "timeFlexibility",
          "styleFlexibility");
  public static final Set<String> PRIVATE_FIELDS =
      Set.of("health", "address", "phone", "negativeFeedback", "secret", "fullPersona", "persona");

  public static String disclosure(String field, String permission) {
    if (PRIVATE_FIELDS.contains(field) || !EDITABLE.contains(field)) return "DENY_PRIVATE";
    if (PUBLIC_FIELDS.contains(field) && "authorized".equals(permission)) return "ALLOW";
    return "ASK_OWNER";
  }

  public static boolean validFact(String f, String v) {
    if (v == null || v.length() > 80) return false;
    return switch (f) {
      case "level" -> Set.of("beginner", "intermediate", "advanced").contains(v);
      case "area" -> Set.of("Barcelona", "Gracia", "Eixample", "Poblenou").contains(v);
      case "availability" -> Set.of("19", "20", "19,20").contains(v);
      case "preferredHour" -> Set.of("19", "20").contains(v);
      case "style" -> Set.of("social", "competitive").contains(v);
      case "side" -> Set.of("left", "right", "either", "strong_left", "strong_right").contains(v);
      case "timeFlexibility", "styleFlexibility" -> Set.of("yes", "ask", "no").contains(v);
      default -> false;
    };
  }

  public static boolean expired(TurnState s, long now) {
    return now >= s.deadline();
  }

  public static boolean bounded(TurnState s) {
    return s.turn() < 8;
  }

  public static String wire(String actor, AgentDecision d) {
    return actor
        + " "
        + d.action()
        + " hour="
        + d.hour()
        + " style="
        + d.style()
        + " side="
        + d.side();
  }
}
