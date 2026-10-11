# Padel matchmaking rules

These rules amend the PadelPot demo requirements. They apply to discovery, agent negotiation, human review, commitment, saved matches and the chat simulator.

## Doubles and court positions

A doubles match has **two teams of two players**. Each team has one left-side place and one right-side place, from that team's perspective facing the opponents. These are court positions, not a player's dominant hand.

| Team | Left-side place | Right-side place |
|---|---|---|
| A | One player | One player |
| B | One player | One player |

There are **two left-side players and two right-side players** overall. Alba taking team A's left place leaves team A's right place and both places on team B open. It does not force three candidates onto the right.

## Declared side preference

| Owner's declaration | Persona `side` value | Negotiation boundary |
|---|---|---|
| Both sides are OK / no preference | `either` | Either side is acceptable. |
| Prefer left, but can occasionally play right | `left` | Prefer a vacant left place; a right-side compromise is permitted. |
| Prefer right, but can occasionally play left | `right` | Prefer a vacant right place; a left-side compromise is permitted. |
| Strong left preference / only left | `strong_left` | Only a left-side place is permitted. |
| Strong right preference / only right | `strong_right` | Only a right-side place is permitted. |

For this demo, **strong means a hard boundary**. An agent cannot override it. An owner can change that declaration through a reviewed persona update. A soft preference permits exploration of the other side, while the owner still approves the final participation. “Either” is a persona preference or an exploratory offer; every approved participation has a concrete left or right position.

Existing saved `left` and `right` preferences retain their previous soft semantics. No stronger preference is inferred without an approved update. Declared preferences, approved inferences and observed behavior retain their existing provenance rules. Playing the other side does not automatically erase a preference or create a public reputation score.

## Negotiation and approval

The organizer owns the match's four team-side places. Agent context lists their vacancies. An offer's side refers to the candidate, including an offer made by the organizer; it does not refer to the organizer's already confirmed position.

Agents may explore a soft compromise and choose another vacant side. They may reject an opportunity with no place compatible with a strong preference. They cannot move confirmed players or assume that all other players must take the organizer's opposite side.

A tentative agreement names the **match, time, team and court side** before human approval. It does not reserve a place. The commitment Command rechecks the exact reviewed place, preference boundary, persona versions, expiry, uniqueness and capacity under the organizer's authority. A concurrent approval that takes that place requires a fresh proposal and approval; no silent side or team switch is allowed.

A match is confirmed only after four explicit human commitments fill A-left, A-right, B-left and B-right exactly once. Team labels are neutral fixture labels, not a ranking or a claim of skill-balanced teams. Court booking remains outside scope.

## Persistence and verification

Canonical `Participation` includes owner, side and team. TPF's existing persistence aspect stores the approved `Match` through its representation mapping. Team assignments survive restart.

Legacy saved matches without team labels receive deterministic labels around their recorded sides. This adds missing team metadata, preserves every recorded side, and supplies no new participation approval. Existing explicit team assignments are preserved. A malformed roster, such as one left player and three right players, is flagged and excluded from further matching; it is not silently repaired. Start a fresh fixture run to replace it.

Verification covers both left places remaining independently available, two valid teams after four approvals, all preference categories, hard-side rejection, soft-side compromises, candidate/organizer role semantics, concurrent claims to the same place, persistence reload and legacy roster handling. Deterministic fixtures validate the rules; they do not prove current Gemma wording or resolve the separate TPF recursive-parent runtime failure.

The amendment passed 36 focused tests (`InvariantTest`, `DoublesTest`, `NativePersistenceTest`), including a native candidate negotiation, correlated approvals and persisted team assignments. The default OpenRouter package and optional Ollama compile passed. Browser checks used explicit UI fixtures for team rendering, preference labels, invalid rosters, composer behavior and approval safety. These checks were run in isolation from the owner's running application; no live Gemma call or complete six-root recording was performed for this amendment.
