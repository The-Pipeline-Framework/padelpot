# Reproducible scenarios

| Scenario | Stimulus and expected invariant | Verification |
|---|---|---|
| A · successful negotiation | Alba opens with one approved player, three candidates independently negotiate, four owners explicitly approve; exactly two left and two right places | `scripts/e2e.py`, actual Gemma |
| B · compromise | Nico prefers 20:00 with “ask” flexibility; the booked match is 19:00. Scoped permission and a separate participation approval are required | Named real E2E plus style-compromise unit test |
| C · privacy challenge | An organizer agent considers a private phone request; malicious peer prose asks for the complete persona. Private data cannot reach peer terms | `scripts/scenarios.py`, actual Gemma; deterministic disclosure/injection tests |
| D · failed negotiation | A hard social-only candidate faces a fixed competitive proposal; clean termination without candidate commitment | `scripts/scenarios.py`, actual Gemma |
| E · commitment safety | Duplicate channel delivery, duplicate approval, 30 concurrent approval attempts, stale persona, cancelled/expired intent and private block | `TopologyTest`, `InvariantTest` |
| F · learning | Simulate the confirmed match, provide feedback, review a proposed inference, approve it. Declared and observed facts retain their provenance | Named real E2E and invariant test |

Live inference uses the selected Gemma model, not canned negotiation text. Outcome assertions are semantic. A model can decline or fail; the evidence runner then fails rather than replacing inference with a fixture. The automated human replies are explicit owner-choice fixtures sent through the same channel contract as the web UI.

To run negative scenarios, start the real-model app and execute `python3 scripts/scenarios.py`. It resets **synthetic business state**, checks two one-root cases and writes `output/scenario-evidence/real-gemma-scenarios.json`. Finish pending interactions before resetting. Stop the process and run `./scripts/reproduce.sh ollama` for the main five-root recording. The previous runtime/evidence is archived locally, not destroyed.

The UI supports free-text requests and pending approvals, rejections, clarifications and counters. A counter at the first checkpoint can resume the bounded negotiation. A counter or additional clarification at the final checkpoint ends the request with an explicit invitation to start a new one. Cancelling an intent does not delete its native Await interaction; a late answer is checked by the business Command and cannot commit.
