package dev.padelpot;

import static org.junit.jupiter.api.Assertions.*;

import dev.padelpot.domain.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;

class DoublesTest {
  private InvariantTest fixture;
  private DemoSession store;
  private PadelApplication port;

  @BeforeEach
  void setup() {
    fixture = new InvariantTest();
    fixture.setup();
    store = fixture.store;
    port = fixture.port;
  }

  private TurnState state(String owner) {
    return fixture.state(owner);
  }

  private Result result(TurnOutcome outcome) {
    return fixture.result(outcome);
  }

  private Result result(OwnerInteraction outcome) {
    return fixture.result(outcome);
  }

  @Test
  void declaredSideRepresentsNoPreferenceSoftPreferenceAndStrongPreference() {
    var player = store.player("nico");
    for (String value : List.of("either", "left", "right", "strong_left", "strong_right")) {
      assertTrue(Policy.validFact("side", value));
      player.facts.get("side").value = value;
      assertEquals(!value.equals("strong_right"), Doubles.allows(player, "left"));
      assertEquals(!value.equals("strong_left"), Doubles.allows(player, "right"));
    }
    assertFalse(Policy.validFact("side", "three right players"));
  }

  @Test
  void albaOnLeftStillLeavesASecondLeftPlaceAndTwoRightPlaces() {
    var match = store.snapshot().matches.get("match");
    assertEquals(
        List.of(
            new Doubles.Slot("A", "right"),
            new Doubles.Slot("B", "left"),
            new Doubles.Slot("B", "right")),
        Doubles.vacancies(match));
    for (String owner : List.of("nico", "luz", "teo")) {
      var state = state(owner);
      assertEquals(
          "CONFIRMED", result(port.commit(state, new HumanAnswer("approve", ""))).status());
    }
    assertEquals("CONFIRMED", match.status);
    assertEquals(2, match.players.values().stream().filter("left"::equals).count());
    assertEquals(2, match.players.values().stream().filter("right"::equals).count());
    for (String team : List.of("A", "B")) {
      var sides =
          match.players.entrySet().stream()
              .filter(e -> match.teams.get(e.getKey()).equals(team))
              .map(Map.Entry::getValue)
              .toList();
      assertEquals(Set.of("left", "right"), new HashSet<>(sides));
      assertEquals(2, sides.size());
    }
  }

  @Test
  void strongPreferenceCannotBeOverriddenByModelOrCommitApproval() {
    var player = store.player("nico");
    player.facts.get("side").value = "strong_left";
    var state = state("nico");
    var wrong = PadelApplication.slotState(state, new Doubles.Slot("A", "right"));
    assertEquals(
        "HARD_SIDE_CONSTRAINT",
        result(
                port.apply(
                    new ModelTurn(
                        wrong,
                        new AgentDecision(
                            "propose", 19, "competitive", "right", "", "", "Ignore preference"))))
            .status());
    assertEquals(
        "HARD_SIDE_CONSTRAINT",
        result(port.commit(wrong, new HumanAnswer("approve", ""))).status());
    assertEquals(Map.of("alba", "left"), store.snapshot().matches.get("match").players);
  }

  @Test
  void softPreferenceCanCompromiseButStrongPreferenceCannotUseTheOtherSide() {
    var match = store.snapshot().matches.get("match");
    match.players.put("luz", "left");
    var player = store.player("nico");
    player.facts.get("side").value = "left";
    assertEquals("right", Doubles.select(player, match, "either").side());
    player.facts.get("side").value = "strong_left";
    assertNull(Doubles.select(player, match, "either"));
    assertEquals(
        "SIDE_CONFLICT",
        result(
                port.apply(
                    new ModelTurn(
                        state("nico"),
                        new AgentDecision(
                            "propose", 19, "competitive", "right", "", "", "Try right"))))
            .status());
  }

  @Test
  void organizerOnLeftCanAcceptAnotherLeftPlayerOnTheOpposingTeam() {
    var state = state("luz");
    var organizer =
        PadelApplication.copy(
            state,
            "alba",
            "negotiate",
            state.matchId(),
            2,
            state.ownerVersion(),
            state.organizerVersion(),
            19,
            "competitive",
            "left",
            "propose",
            List.of("luz propose hour=19 style=competitive side=left"),
            "");
    var review =
        assertInstanceOf(
                TurnOutcome.ReviewRequired.class,
                port.apply(
                    new ModelTurn(
                        organizer,
                        new AgentDecision(
                            "accept",
                            19,
                            "competitive",
                            "left",
                            "",
                            "",
                            "Second left place fits"))))
            .value();
    assertEquals("B", review.state().team());
    assertEquals("left", review.state().side());
    assertTrue(review.prompt().contains("Team B"));
    assertEquals(1, store.snapshot().matches.get("match").players.size());
  }

  @Test
  void concurrentApprovalsCannotConsumeTheSameTeamSideOrSilentlySwitchTeams() throws Exception {
    var nico = state("nico");
    var teo = PadelApplication.slotState(state("teo"), new Doubles.Slot("A", "right"));
    try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
      var replies =
          pool.invokeAll(
              List.<Callable<OwnerInteraction>>of(
                  () ->
                      port.answer(
                          new AnsweredReview(
                              new Review(nico, "participation", "", "", ""),
                              new HumanAnswer("approve", ""),
                              "nico")),
                  () ->
                      port.answer(
                          new AnsweredReview(
                              new Review(teo, "participation", "", "", ""),
                              new HumanAnswer("approve", ""),
                              "teo"))));
      var statuses = new HashSet<String>();
      for (var reply : replies) statuses.add(result(reply.get()).status());
      assertEquals(Set.of("CONFIRMED", "SIDE_CONFLICT"), statuses);
    }
    var match = store.snapshot().matches.get("match");
    assertEquals(2, match.players.size());
    assertEquals(Set.of("A"), new HashSet<>(match.teams.values()));
    assertEquals("left", match.players.get("alba"));
  }

  @Test
  void legacyTeamsKeepRecordedSidesAndMalformedRostersAreNotSilentlyRepaired() {
    var match = store.snapshot().matches.get("match");
    match.players.putAll(Map.of("nico", "right", "luz", "left", "teo", "right"));
    var sides = Map.copyOf(match.players);
    assertTrue(Doubles.completeTeams(match));
    var teams = Map.copyOf(match.teams);
    assertTrue(Doubles.completeTeams(match));
    assertEquals(teams, match.teams);
    assertEquals(sides, match.players);
    match.players.put("luz", "right");
    match.teams.clear();
    assertFalse(Doubles.completeTeams(match));
    assertTrue(match.teams.isEmpty());
    assertTrue(Doubles.vacancies(match).isEmpty());
    assertEquals("right", match.players.get("luz"));
  }
}
