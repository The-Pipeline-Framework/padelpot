# Synthetic demo fixtures

The authoritative fixture loader is `DemoStore.reset(false)`. `POST /api/demo/reset` invokes it; `onboarding:true` leaves editable preferences empty for the conversational interview. All identities, preferences and private canaries are invented. Every initial editable fact is declared; model-generated learning is inferred only after approval, and a simulated played-match hour is observed behavior.

| Owner | Level / area | Hard available hours | Preferred hour | Style | Side | Time flexibility |
|---|---|---|---|---|---|---|
| Alba Sol | intermediate / Barcelona | 19,20 | 19 | competitive | left | yes |
| Nico Rios | intermediate / Barcelona | 19,20 | 20 | competitive | right | ask |
| Luz Vega | intermediate / Barcelona | 19,20 | 19 | competitive | left | yes |
| Teo Mar | intermediate / Barcelona | 19,20 | 19 | competitive | either | yes |

Style flexibility starts at `ask`. Level, broad area, style and side are authorized for match coordination; availability and flexibility require permission for field disclosure. Match proposals necessarily communicate proposed time and style under the find/open intent. Every owner also has a synthetic private canary excluded from model input and peer messages.

The privacy fixture preloads one fictional organizer approval and asks an actual Gemma organizer agent to consider requesting the candidate phone. It also supplies an adversarial peer message. The model can refuse the request, or request the field and encounter deterministic denial. It cannot approve disclosure or participation. The incompatibility fixture gives Nico a hard social-only constraint against a competitive match. The selected model still performs inference before the outcome is checked. Neither fixture predetermines dialogue or success.

See `scripts/e2e.py` and `scripts/scenarios.py` for reproducible semantic checks. `FixtureModel` is exclusively the deterministic business-test model; it is never a provider fallback or evidence of live inference.
