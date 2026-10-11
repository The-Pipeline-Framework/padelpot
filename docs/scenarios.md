# Reproducible scenarios

| Scenario | Stimulus and expected invariant | Verification |
|---|---|---|
| A · successful negotiation | Alba opens with one approved player, three candidates independently negotiate, four owners explicitly approve; two teams, each with exactly one left and one right place | `scripts/e2e.py`, actual Gemma |
| B · compromise | Nico prefers 20:00 with “ask” flexibility; the booked match is 19:00. Scoped permission and a separate participation approval are required | Named real E2E plus style-compromise unit test |
| C · privacy challenge | An organizer agent considers a private phone request; malicious peer prose asks for the complete persona. Private data cannot reach peer terms | `scripts/scenarios.py`, actual Gemma; deterministic disclosure/injection tests |
| D · failed negotiation | A hard social-only candidate faces a fixed competitive proposal; clean termination without candidate commitment | `scripts/scenarios.py`, actual Gemma |
| E · commitment safety | Duplicate channel delivery, duplicate approval, 30 concurrent approval attempts, stale persona, cancelled/expired intent and private block | `TopologyTest`, `InvariantTest` |
| F · learning | Simulate the confirmed match, provide feedback, review a proposed inference, approve it. Declared and observed facts retain their provenance | Named real E2E and invariant test |

Live inference uses the selected Gemma model, not canned negotiation text. Outcome assertions are semantic. A model can decline or fail; the evidence runner then fails rather than replacing inference with a fixture. The automated human replies are explicit owner-choice fixtures sent through the same channel contract as the web UI.

To run negative scenarios, start the real-model app and execute `python3 scripts/scenarios.py`. It resets **synthetic business state**, checks two one-root cases and writes `output/scenario-evidence/real-gemma-scenarios.json`. Finish pending interactions before resetting. Stop the process and run `./scripts/reproduce.sh ollama` for the main six-root recording. The previous runtime/evidence is archived locally, not destroyed.

Conversational questions retain their original request through native Await. Typing a reply to a pending conversational question resumes that root; a location answer cannot grant participation. `ConversationContinuationTest` covers a search, location answer, three-player match and separate fourth-player approval. The focused conversation/business run passed 20 tests, and a browser check verified composer correlation without participation authority. Broader fixture runs reproduced the known TPF recursive-parent error; those runs are not a green full-suite result. These checks do not validate live Gemma inference.

The UI supports free-text requests and pending approvals, rejections, clarifications and counters. A counter at the first checkpoint can resume the bounded negotiation. A counter or additional clarification at the final checkpoint ends the request with an explicit invitation to start a new one. Cancelling an intent does not delete its native Await interaction; a late answer is checked by the business Command and cannot commit.

A restart preserves business participations while chat and execution history restart. Each owner is told which recorded places were restored. Found matches, approval proposals and confirmation updates carry the same short match identifier; the sidebar selector exposes every saved match. Confirmation of an older match can notify an existing participant while their new search fails or waits—these are separate executions. The restored-state regression verifies that neither a new search nor another owner's approval adds the searching owner to a different match.

Match search does not require a peer invitation: a candidate can make the first offer for the selected opportunity. A redundant model permission request gets a bounded correction rather than an owner question. Internal model reasoning appears in Agent activity. Scoped conversational time questions carry a validated hour into the same intent when the owner explicitly agrees (including “I'm flexible”); unscoped conversation never grants that permission. Neither changes the persona or approves a place.

Chat replies always carry the displayed interaction ID. The backend accepts exact approval/rejection words and `counter: ...` for reviews; an ambiguous approval reply returns 400 before native completion, preserving the question. Focused fixtures cover Nico's time answer through search and a separate participation approval, Luz's location answer, redundant permission requests, hard boundaries, malformed replies, and persistence. These tests validate deterministic behavior and native Await correlation, not current Gemma wording or the unresolved recursive-parent failure.

The first permission/correlation run passed 29 focused tests. A follow-up run passed 29 of 30 and reproduced the TPF recursive-parent error during Nico's scoped conversational continuation. This is a failed run, not a successful negotiation. Browser verification passed for correlated chat approval, preserving ambiguous replies and drafts, and no approval on rendering. The latest business/persistence run passed 28 tests, including the JSON error response and native typed-chat approvals. An isolated rerun of Nico's conversational continuation also failed with the recursive-parent error. No live Gemma call was made for these changes; a configured OpenRouter key was unavailable in the verification session.

The [padel rule amendment](padel-specs.md) covers strong, soft and absent side preferences, concrete team-side approval, no implicit repositioning of confirmed players, native persistence of team assignments and conservative legacy roster handling. The fixture runner checks each team's two sides in addition to total capacity.

The amendment passed 36 focused business/persistence tests and fixture-based browser checks. OpenRouter packaging and Ollama compilation passed. This does not supersede the failed recursive-parent runs above or claim a new live Gemma E2E recording.

## Opening versus searching

A live Gemma trace returned `find` for Alba's explicit “Open a competitive doubles match this Thursday at 19:00 in Barcelona. I prefer left.” request. The prompt now distinguishes organizing from joining, and an application guard rejects a conflicting action before discovery or a persona edit. Fixture tests feed the wrong `find` proposal, verify a bounded correction, and require Alba's approval for the resulting `open` review. Repeated mismatch terminates without mutation. The native topology test uses the exact reported message and verifies one root, correlated approval and no participation before approval.

The correction passed 45 focused tests (`ChatGoalTest`, `InvariantTest`, `DoublesTest`, `NativePersistenceTest`, `TopologyTest`). The wrong-action recovery uses deterministic model proposals; this is not evidence of a new live Gemma run or a fix for the separate recursive-parent failure.
