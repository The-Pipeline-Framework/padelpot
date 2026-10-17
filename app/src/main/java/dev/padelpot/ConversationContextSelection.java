package dev.padelpot;

import dev.padelpot.domain.ModelInput;
import dev.padelpot.domain.TurnState;
import dev.padelpot.persistence.AggregateQueries.HistoryTurn;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.pipelineframework.connector.conversation.ContextAssembler;
import org.pipelineframework.connector.conversation.ContextAssemblyRequest;
import org.pipelineframework.connector.conversation.ContextAssemblyResult;
import org.pipelineframework.connector.conversation.ContextCandidate;
import org.pipelineframework.connector.conversation.ConversationScope;

/** Pure selection of approved, owner-scoped history for one captured model Query. */
final class ConversationContextSelection {
  static final int HISTORY_READ_LIMIT = 100;
  private static final int MODEL_WINDOW = 16_384;
  private static final int RESERVED_OUTPUT = 2_048;
  static final int INSTRUCTION_ALLOWANCE = 6_000;
  private static final int SCHEMA_ALLOWANCE = 1_000;
  private static final int MAX_ENTRY_BYTES = 4_096;

  record Selection(ModelInput input, ContextAssemblyResult assembly) {}

  Selection select(TurnState state, ModelInput base, List<HistoryTurn> history) {
    var scope = new ConversationScope("padelpot-demo", state.actorId(), state.actorId());
    var candidates = new ArrayList<ContextCandidate>();
    addRequired(scope, candidates, "task", "task", base.task());
    addRequired(scope, candidates, "brief", "player-brief", base.playerBrief());
    addRequired(scope, candidates, "terms", "public-terms", base.publicTerms());
    for (int index = 0; index < base.peerMessages().size(); index++) {
      addRequired(scope, candidates, "peer-" + index, "peer-message", base.peerMessages().get(index));
    }
    addRequired(scope, candidates, "current", "current-owner-message", base.ownerMessage());
    // The assembler consumes priority order. Present the selected turns chronologically to the model.
    for (int index = history.size() - 1; index >= 0; index--) {
      HistoryTurn turn = history.get(index);
      if (!turn.speaker().equals("owner") && !turn.speaker().equals("agent")) {
        throw new IllegalArgumentException("Unexpected conversation speaker");
      }
      candidates.add(new ContextCandidate(scope, "turn-" + turn.cursor(), turn.speaker(),
          turn.text(), Optional.empty(), false));
    }
    var request = new ContextAssemblyRequest(scope, candidates, MODEL_WINDOW,
        INSTRUCTION_ALLOWANCE, SCHEMA_ALLOWANCE, RESERVED_OUTPUT, MAX_ENTRY_BYTES);
    ContextAssemblyResult assembly = new ContextAssembler().assemble(request);
    if (!assembly.fits()) {
      throw new IllegalArgumentException("Conversation context cannot fit model window: "
          + assembly.overflowReason().orElseThrow());
    }
    var selected = new ArrayList<String>();
    for (int index = assembly.selected().size() - 1; index >= 0; index--) {
      var entry = assembly.selected().get(index);
      if (entry.provenance().equals("owner") || entry.provenance().equals("agent")) {
        selected.add(entry.provenance() + ": " + entry.text());
      }
    }
    return new Selection(new ModelInput(base.state(), base.task(), base.playerBrief(),
        base.publicTerms(), base.peerMessages(), base.ownerMessage(), selected), assembly);
  }

  private static void addRequired(ConversationScope scope, List<ContextCandidate> candidates,
      String id, String provenance, String text) {
    if (!text.isBlank()) {
      candidates.add(new ContextCandidate(scope, id, provenance, text, Optional.empty(), true));
    }
  }
}
