package dev.padelpot;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.*;
import java.util.*;

/**
 * Mutable working copies and bounded simulator history. TPF persists emitted business aggregates.
 */
@ApplicationScoped
public class DemoSession {
  @Inject dev.padelpot.persistence.AggregateQueries aggregates;

  public static class Fact {
    public String value, source, disclosure;

    public Fact() {}

    public Fact(String v, String s, String d) {
      value = v;
      source = s;
      disclosure = d;
    }
  }

  public static class Player {
    public String id, name, color;
    public dev.padelpot.domain.ChangeReceipt change =
        new dev.padelpot.domain.ChangeReceipt("fixture", "", "FIXTURE", "Synthetic state");
    public long version = 1;
    public Map<String, Fact> facts = new LinkedHashMap<>();
    public Set<String> blocked = new LinkedHashSet<>();
    public List<Chat> chat = new ArrayList<>();

    public Player() {}

    public String value(String f) {
      return facts.containsKey(f) ? facts.get(f).value : "";
    }
  }

  public record Chat(String id, String role, String text, String time) {}

  public static class Match {
    public String id, organizer, style, area, date, level, status = "OPEN";
    public int hour;
    public dev.padelpot.domain.ChangeReceipt change =
        new dev.padelpot.domain.ChangeReceipt("fixture", "", "FIXTURE", "Synthetic state");
    public long version = 1;
    public Map<String, String> players = new LinkedHashMap<>();
    public Map<String, String> teams = new LinkedHashMap<>();
    public Set<String> restoredPlayers = new HashSet<>();
    public boolean cancelled, played;
  }

  public static class Intent {
    public String requestId, ownerId, matchId;
    public long deadline, ownerVersion, organizerVersion;
    public boolean cancelled;
    public Set<Integer> permittedHours = new HashSet<>();
    public Set<String> disclosures = new HashSet<>();
    public Set<String> permittedStyles = new HashSet<>();
  }

  public record Event(String type, Map<String, Object> data, String time) {}

  public static class Snapshot {
    public Map<String, Player> players = new LinkedHashMap<>();
    public Map<String, Match> matches = new LinkedHashMap<>();
    public Map<String, Intent> intents = new LinkedHashMap<>();
    public List<Event> events = new ArrayList<>();
    public Set<String> applied = new HashSet<>();
  }

  public Snapshot db = new Snapshot();

  public Snapshot snapshot() {
    return db;
  }

  @PostConstruct
  void load() {
    seed(false);
    for (var owner : aggregates.owners()) {
      var p = player(owner.id());
      p.name = owner.name();
      p.color = owner.color();
      p.version = owner.version();
      p.change = owner.change();
      p.facts.clear();
      owner
          .facts()
          .forEach(f -> p.facts.put(f.field(), new Fact(f.value(), f.source(), f.disclosure())));
      p.blocked.addAll(owner.blocked());
    }
    for (var match : aggregates.matches()) {
      var m = new Match();
      m.id = match.id();
      m.organizer = match.organizer();
      m.style = match.style();
      m.area = match.area();
      m.date = match.date();
      m.level = match.level();
      m.status = match.status();
      m.hour = match.hour();
      m.version = match.version();
      m.cancelled = match.cancelled();
      m.played = match.played();
      m.change = match.change();
      match.players().forEach(p -> m.players.put(p.ownerId(), p.side()));
      match.players().stream()
          .filter(p -> !p.team().isBlank())
          .forEach(p -> m.teams.put(p.ownerId(), p.team()));
      if (!Doubles.completeTeams(m)) event("INVALID_RESTORED_ROSTER", Map.of("matchId", m.id));
      m.restoredPlayers.addAll(m.players.keySet());
      db.matches.put(m.id, m);
      for (String owner : m.players.keySet())
        message(
            owner,
            "agent",
            "Restored your recorded participation in match "
                + matchLabel(m.id)
                + ": "
                + m.date
                + " at "
                + m.hour
                + ":00, "
                + m.area
                + ", "
                + m.style
                + ". This place was recorded before this chat session. Match status: "
                + (Doubles.valid(m) ? m.status : "INVALID_ROSTER").toLowerCase()
                + ".");
    }
  }

  public synchronized void reset(boolean onboarding) {
    if (aggregates != null) aggregates.clearFixtures();
    seed(onboarding);
  }

  public static String matchLabel(String id) {
    String suffix = id.substring(id.lastIndexOf(':') + 1);
    return "#" + suffix.substring(0, Math.min(8, suffix.length()));
  }

  private void seed(boolean onboarding) {
    db = new Snapshot();
    String[] ids = {"alba", "nico", "luz", "teo"},
        names = {"Alba Sol", "Nico Rios", "Luz Vega", "Teo Mar"},
        colors = {"#c4e887", "#b8caff", "#ffc8b8", "#ebcefa"};
    for (int i = 0; i < 4; i++) {
      Player p = new Player();
      p.id = ids[i];
      p.name = names[i];
      p.color = colors[i];
      if (!onboarding) {
        fact(p, "level", "intermediate");
        fact(p, "area", "Barcelona");
        fact(p, "availability", "19,20");
        fact(p, "preferredHour", i == 1 ? "20" : "19");
        fact(p, "style", "competitive");
        fact(p, "side", i == 0 ? "strong_left" : i == 2 ? "left" : i == 1 ? "right" : "either");
        fact(p, "timeFlexibility", i == 1 ? "ask" : "yes");
        fact(p, "styleFlexibility", "ask");
      }
      p.facts.put(
          "secret", new Fact("PRIVATE_CANARY_" + p.id.toUpperCase(), "declared", "private"));
      db.players.put(p.id, p);
      message(
          p.id,
          "agent",
          "Hola, "
              + p.name.split(" ")[0]
              + ". I'm your private padel agent. Tell me about your level, area and when you like"
              + " to play. You choose what to share, and every match needs your approval.");
    }
  }

  static void fact(Player p, String f, String v) {
    p.facts.put(
        f, new Fact(v, "declared", Policy.PUBLIC_FIELDS.contains(f) ? "authorized" : "permission"));
  }

  public Player player(String id) {
    Player p = db.players.get(id);
    if (p == null) throw new IllegalArgumentException("Unknown fictional owner");
    return p;
  }

  public void message(String owner, String role, String text) {
    Player p = player(owner);
    p.chat.add(new Chat(UUID.randomUUID().toString(), role, text, Instant.now().toString()));
    if (p.chat.size() > 100) p.chat.removeFirst();
  }

  public void event(String type, Map<String, Object> data) {
    db.events.add(new Event(type, Map.copyOf(data), Instant.now().toString()));
    if (db.events.size() > 300) db.events.removeFirst();
  }

  public dev.padelpot.domain.Owner ownerValue(String id) {
    var p = player(id);
    return new dev.padelpot.domain.Owner(
        p.id,
        p.name,
        p.color,
        p.version,
        p.facts.entrySet().stream()
            .map(
                e ->
                    new dev.padelpot.domain.PersonaFact(
                        e.getKey(),
                        e.getValue().value,
                        e.getValue().source,
                        e.getValue().disclosure))
            .toList(),
        List.copyOf(p.blocked),
        p.change);
  }

  public dev.padelpot.domain.Match matchValue(String id) {
    var m = db.matches.get(id);
    Doubles.completeTeams(m);
    return new dev.padelpot.domain.Match(
        m.id,
        m.organizer,
        m.style,
        m.area,
        m.date,
        m.level == null ? "" : m.level,
        m.status,
        m.hour,
        m.version,
        m.players.entrySet().stream()
            .map(
                e ->
                    new dev.padelpot.domain.Participation(
                        e.getKey(), e.getValue(), m.teams.getOrDefault(e.getKey(), "")))
            .toList(),
        m.cancelled,
        m.played,
        m.change);
  }

  public record MatchView(
      String id,
      String organizer,
      String style,
      String area,
      String date,
      String level,
      String status,
      int hour,
      long version,
      Map<String, String> players,
      Map<String, String> teams,
      boolean cancelled,
      boolean played,
      boolean rosterValid) {}

  public Map<String, Map<String, String>> personaView(String id) {
    var view = new LinkedHashMap<String, Map<String, String>>();
    player(id)
        .facts
        .forEach(
            (field, fact) ->
                view.put(
                    field,
                    Map.of(
                        "value",
                        fact.value,
                        "source",
                        fact.source,
                        "disclosure",
                        fact.disclosure)));
    return Collections.unmodifiableMap(view);
  }

  public record IntentView(
      String requestId,
      String ownerId,
      String matchId,
      long deadline,
      long ownerVersion,
      long organizerVersion,
      boolean cancelled,
      Set<Integer> permittedHours,
      Set<String> disclosures,
      Set<String> permittedStyles) {
    static IntentView of(Intent intent) {
      return new IntentView(
          intent.requestId,
          intent.ownerId,
          intent.matchId,
          intent.deadline,
          intent.ownerVersion,
          intent.organizerVersion,
          intent.cancelled,
          Set.copyOf(intent.permittedHours),
          Set.copyOf(intent.disclosures),
          Set.copyOf(intent.permittedStyles));
    }
  }

  public synchronized Map<String, Object> publicView() {
    return Map.of(
        "owners",
        db.players.values().stream()
            .map(p -> Map.of("id", p.id, "name", p.name, "color", p.color))
            .toList(),
        "matches",
        db.matches.values().stream()
            .map(
                m ->
                    new MatchView(
                        m.id,
                        m.organizer,
                        m.style,
                        m.area,
                        m.date,
                        m.level,
                        Doubles.valid(m) ? m.status : "INVALID_ROSTER",
                        m.hour,
                        m.version,
                        Collections.unmodifiableMap(new LinkedHashMap<>(m.players)),
                        Collections.unmodifiableMap(new LinkedHashMap<>(m.teams)),
                        m.cancelled,
                        m.played,
                        Doubles.valid(m)))
            .toList());
  }
}
