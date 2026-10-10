package dev.padelpot;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/** Application-owned versioned personas and one organizer aggregate; not a TPF execution store. */
@ApplicationScoped
public class DemoStore {
  @Inject ObjectMapper json;

  @ConfigProperty(name = "padelpot.state-file")
  String file;

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
    public long version = 1;
    public Map<String, String> players = new LinkedHashMap<>();
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
    try {
      Path p = Path.of(file);
      if (Files.exists(p)) db = json.readValue(p.toFile(), Snapshot.class);
      else reset(false);
    } catch (Exception e) {
      throw new IllegalStateException("Could not load demo business state", e);
    }
  }

  public synchronized void reset(boolean onboarding) {
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
        fact(p, "side", i == 0 || i == 2 ? "left" : i == 1 ? "right" : "either");
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
    save();
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

  public synchronized void save() {
    try {
      Path p = Path.of(file);
      Files.createDirectories(p.toAbsolutePath().getParent());
      Path temp = p.resolveSibling(p.getFileName() + ".tmp");
      json.writerWithDefaultPrettyPrinter().writeValue(temp.toFile(), db);
      Files.move(temp, p, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    } catch (Exception e) {
      throw new IllegalStateException("Could not persist demo state", e);
    }
  }

  public synchronized Map<String, Object> publicView() {
    return Map.of(
        "owners",
        db.players.values().stream()
            .map(p -> Map.of("id", p.id, "name", p.name, "color", p.color))
            .toList(),
        "matches",
        new ArrayList<>(db.matches.values()));
  }
}
