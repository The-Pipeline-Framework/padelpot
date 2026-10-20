package dev.padelpot;

import static org.junit.jupiter.api.Assertions.*;

import dev.padelpot.domain.ModelInput;
import dev.padelpot.domain.TurnState;
import dev.padelpot.persistence.AggregateQueries.HistoryTurn;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

class ConversationContextSelectionTest {
  private final ConversationContextSelection selector = new ConversationContextSelection();

  @Test
  void keepsRecentTurnsWithinBudgetInChronologicalPresentationOrder() {
    var state = state("What did we decide about Thursday?");
    var history = new ArrayList<HistoryTurn>();
    for (int index = 0; index < 100; index++) {
      history.add(new HistoryTurn(index + 1, "event-" + index, "owner",
          "turn-" + index + " " + "a".repeat(300), "2026-10-17T00:00:00Z"));
    }
    var selected = selector.select(state, base(state), history);
    assertTrue(selected.assembly().fits());
    assertTrue(selected.assembly().estimatedInputTokens()
        + selected.assembly().reservedOutputTokens() <= 16_384);
    assertTrue(selected.input().recentHistory().size() < history.size());
    assertTrue(selected.input().recentHistory().getLast().contains("turn-99"));
    assertFalse(selected.input().recentHistory().getFirst().contains("turn-0"));
    var again = selector.select(state, base(state), history);
    assertEquals(selected, again);
    assertTrue(selected.assembly().skipped().stream()
        .allMatch(entry -> entry.reason().equals("input-budget-exhausted")));
  }

  @Test
  void dropsAnOversizedEarlierTurnWithoutLosingTheLaterCorrection() {
    var state = state("What did I correct?");
    var history = List.of(
        new HistoryTurn(1, "old", "agent", "x".repeat(4_097), "2026-10-17T00:00:00Z"),
        new HistoryTurn(2, "correction", "owner", "Actually, Thursday at 20:00",
            "2026-10-17T00:00:01Z"));
    var selected = selector.select(state, base(state), history);
    assertEquals(List.of("owner: Actually, Thursday at 20:00"), selected.input().recentHistory());
    assertEquals("oversized-without-owned-reference", selected.assembly().skipped().getFirst().reason());
  }

  @Test
  void rejectsARequiredCurrentMessageThatCannotBeSentWhole() {
    var correlated = state("x".repeat(2_500));
    assertEquals(correlated.ownerText(),
        selector.select(correlated, base(correlated), List.of()).input().ownerMessage());
    var state = state("x".repeat(4_097));
    assertThrows(IllegalArgumentException.class,
        () -> selector.select(state, base(state), List.of()));
  }

  @Test
  void cannotDropKnownPeerContextToMakeHistoryFit() {
    var state = state("What happened?");
    var ordinary = base(state);
    var input = new ModelInput(state, ordinary.task(), ordinary.playerBrief(),
        ordinary.publicTerms(), List.of("x".repeat(4_097)), ordinary.ownerMessage(), List.of());
    assertThrows(IllegalArgumentException.class,
        () -> selector.select(state, input, List.of()));
  }

  @Test
  void authoredInstructionsFitTheAssemblyAllowance() throws Exception {
    for (String file : List.of("pipeline.yaml", "pipeline.ollama.yaml", "pipeline.fixture.yaml")) {
      Map<?, ?> document = new Yaml().load(
          Files.readString(Path.of("src/main/resources", file)));
      Map<?, ?> definitions = (Map<?, ?>) document.get("pipelines");
      Map<?, ?> agent = (Map<?, ?>) definitions.get("personal-agent");
      List<?> steps = (List<?>) agent.get("steps");
      Map<?, ?> model = steps.stream().map(step -> (Map<?, ?>) step)
          .filter(step -> "Gemma proposes one action".equals(step.get("name")))
          .findFirst().orElseThrow();
      String instructions = (String) ((Map<?, ?>) model.get("config")).get("instructions");
      assertTrue(instructions.getBytes(StandardCharsets.UTF_8).length
          <= ConversationContextSelection.INSTRUCTION_ALLOWANCE, file);
    }
  }

  private static TurnState state(String message) {
    return new TurnState("alba:request", "alba", "alba", "chat", "", 0,
        1_800_000L, 1L, 0L, 19, "competitive", "left", "", List.of(), message, "");
  }

  private static ModelInput base(TurnState state) {
    return new ModelInput(state, "CHAT", "level=intermediate", "Thursday 19:00", List.of(),
        state.ownerText(), List.of());
  }
}
