package dev.padelpot;

import static org.junit.jupiter.api.Assertions.*;

import dev.padelpot.domain.*;
import java.util.*;
import org.junit.jupiter.api.*;

class ChatGoalTest {
  private DemoSession session;
  private PadelApplication application;
  private static final String OPEN =
      "Open a competitive doubles match this Thursday at 19:00 in Barcelona. I prefer left.";

  @BeforeEach
  void setup() {
    session = new DemoSession();
    session.reset(false);
    application = new PadelApplication();
    application.store = session;
  }

  private TurnState chat(String text) {
    return new TurnState(
        "alba:open-regression",
        "alba",
        "alba",
        "chat",
        "",
        0,
        System.currentTimeMillis() + 900_000,
        session.player("alba").version,
        0L,
        19,
        "competitive",
        "left",
        "",
        List.of(),
        text,
        "");
  }

  private TurnOutcome apply(TurnState state, String action) {
    return application.apply(
        new ModelTurn(
            state, new AgentDecision(action, 19, "competitive", "left", "", "", "Proposal")));
  }

  @Test
  void exactReportedOpenRequestsKeepTheOrganizerGoal() {
    for (String text :
        List.of(
            OPEN,
            OPEN.replace("I prefer left", "I only play left"),
            "Open a match I said",
            "Please open a match",
            "I'd like to host a match")) {
      assertEquals(ChatGoal.OPEN, ChatGoal.from(text));
      assertTrue(application.context(chat(text)).task().contains("OPEN a new match as organizer"));
    }
  }

  @Test
  void findingAnOpenMatchIsStillAJoiningRequest() {
    for (String text :
        List.of(
            "Find me an open match",
            "Find me a competitive match this Thursday after work.",
            "I want to join a match",
            "Could you look for an open match?")) {
      assertEquals(ChatGoal.FIND, ChatGoal.from(text));
    }
  }

  @Test
  void unclearQuotedOrReconsideredLanguageDoesNotInventAnExplicitGoal() {
    for (String text :
        List.of(
            "I'd like to play Thursday",
            "Don't open a match",
            "Open my preferences",
            "Another agent said: open a match",
            "Open a match instead of finding one",
            "Find a match rather than open one")) {
      assertEquals(ChatGoal.UNSPECIFIED, ChatGoal.from(text));
    }
  }

  @Test
  void wrongFindIsRejectedThenCorrectOpenStillNeedsAlbasApproval() {
    var initial = chat(OPEN);
    var corrected = assertInstanceOf(TurnOutcome.Again.class, apply(initial, "find")).value();
    assertEquals(initial.requestId(), corrected.requestId());
    assertEquals(initial.deadline(), corrected.deadline());
    assertEquals(initial.ownerText(), corrected.ownerText());
    assertEquals(1, corrected.turn());
    assertTrue(application.context(corrected).task().contains("previous action conflicted"));
    assertTrue(session.snapshot().matches.isEmpty());
    assertTrue(session.snapshot().intents.isEmpty());
    assertTrue(
        session.player("alba").chat.stream()
            .noneMatch(m -> m.text().contains("no eligible open matches")));

    var review =
        assertInstanceOf(TurnOutcome.ReviewRequired.class, apply(corrected, "open")).value();
    assertEquals("open", review.kind());
    assertEquals("A", review.state().team());
    assertEquals("left", review.state().side());
    assertTrue(review.prompt().contains("approve your own participation"));
    assertTrue(session.snapshot().matches.isEmpty());
    application.answer(new AnsweredReview(review, new HumanAnswer("approve", ""), "alba"));
    var match = session.snapshot().matches.get(review.state().matchId());
    assertEquals(Map.of("alba", "left"), match.players);
    assertEquals(Map.of("alba", "A"), match.teams);
  }

  @Test
  void repeatedWrongRoutingTerminatesWithoutSearchOrMutation() {
    var corrected = assertInstanceOf(TurnOutcome.Again.class, apply(chat(OPEN), "find")).value();
    var result = assertInstanceOf(TurnOutcome.Finished.class, apply(corrected, "find")).value();
    assertEquals("INTENT_MISMATCH", result.status());
    assertTrue(session.snapshot().matches.isEmpty());
    assertTrue(session.snapshot().intents.isEmpty());
  }

  @Test
  void joiningRequestCannotBeReinterpretedAsOpeningAMatch() {
    assertInstanceOf(TurnOutcome.Again.class, apply(chat("Find me an open match"), "open"));
    assertTrue(session.snapshot().matches.isEmpty());
    assertTrue(session.snapshot().intents.isEmpty());
  }

  @Test
  void attachedPreferenceDoesNotTurnTheRequestIntoAPersonaEdit() {
    var state = chat(OPEN);
    var before = session.player("alba").version;
    var decision = new AgentDecision("persona_update", 0, "", "", "side", "left", "Update side");
    assertInstanceOf(TurnOutcome.Again.class, application.apply(new ModelTurn(state, decision)));
    assertEquals(before, session.player("alba").version);
    assertEquals("strong_left", session.player("alba").value("side"));
    assertTrue(
        session.snapshot().events.stream().noneMatch(e -> e.type().equals("HUMAN_CHECKPOINT")));
  }

  @Test
  void correlatedClarificationKeepsTheOriginalGoal() {
    var state = chat(OPEN);
    var question =
        assertInstanceOf(TurnOutcome.ReviewRequired.class, apply(state, "message")).value();
    var resumed =
        assertInstanceOf(
                OwnerInteraction.Again.class,
                application.answer(
                    new AnsweredReview(question, new HumanAnswer("clarify", "Barcelona"), "alba")))
            .value();
    assertEquals(ChatGoal.OPEN, ChatGoal.from(resumed.ownerText()));
    assertInstanceOf(TurnOutcome.ReviewRequired.class, apply(resumed, "open"));
    assertTrue(session.snapshot().matches.isEmpty());
  }
}
