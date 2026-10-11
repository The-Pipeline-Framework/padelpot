package dev.padelpot;

import dev.padelpot.DemoSession.Match;
import dev.padelpot.DemoSession.Player;
import java.util.*;

/** Deterministic padel rules: two teams, each with one left and one right place. */
public final class Doubles {
  private Doubles() {}

  public record Slot(String team, String side) {}

  private static final List<Slot> SLOTS =
      List.of(
          new Slot("A", "left"), new Slot("A", "right"),
          new Slot("B", "left"), new Slot("B", "right"));

  public static String preferredSide(Player player) {
    return switch (player.value("side")) {
      case "left", "strong_left" -> "left";
      case "right", "strong_right" -> "right";
      default -> "either";
    };
  }

  public static boolean allows(Player player, String side) {
    return switch (player.value("side")) {
      case "strong_left" -> side.equals("left");
      case "strong_right" -> side.equals("right");
      default -> Set.of("left", "right").contains(side);
    };
  }

  /** Fill only missing team labels in legacy state; never move an existing assigned place. */
  public static boolean completeTeams(Match match) {
    Map<String, String> completed = resolveTeams(match);
    if (completed == null) return false;
    match.teams.clear();
    match.teams.putAll(completed);
    return true;
  }

  private static Map<String, String> resolveTeams(Match match) {
    if (match.players.size() > 4
        || ((match.status.equals("CONFIRMED") || match.played) && match.players.size() != 4)
        || !match.players.keySet().containsAll(match.teams.keySet())) return null;
    Map<String, String> completed = new LinkedHashMap<>(match.teams);
    Set<Slot> occupied = new HashSet<>();
    for (var entry : completed.entrySet()) {
      Slot slot = new Slot(entry.getValue(), match.players.get(entry.getKey()));
      if (!SLOTS.contains(slot) || !occupied.add(slot)) return null;
    }
    for (String owner : match.players.keySet().stream().sorted().toList()) {
      if (completed.containsKey(owner)) continue;
      String side = match.players.get(owner);
      Slot slot =
          SLOTS.stream()
              .filter(s -> s.side().equals(side) && !occupied.contains(s))
              .findFirst()
              .orElse(null);
      if (slot == null) return null;
      completed.put(owner, slot.team());
      occupied.add(slot);
    }
    return completed;
  }

  public static List<Slot> vacancies(Match match) {
    Map<String, String> teams = resolveTeams(match);
    if (teams == null) return List.of();
    Set<Slot> occupied = new HashSet<>();
    match.players.forEach((owner, side) -> occupied.add(new Slot(teams.get(owner), side)));
    return SLOTS.stream().filter(slot -> !occupied.contains(slot)).toList();
  }

  public static boolean valid(Match match) {
    return resolveTeams(match) != null;
  }

  public static Slot select(Player player, Match match, String proposedSide) {
    List<Slot> allowed =
        vacancies(match).stream()
            .filter(slot -> allows(player, slot.side()))
            .filter(slot -> proposedSide.equals("either") || slot.side().equals(proposedSide))
            .toList();
    return allowed.stream()
        .filter(slot -> slot.side().equals(preferredSide(player)))
        .findFirst()
        .orElse(allowed.isEmpty() ? null : allowed.getFirst());
  }
}
