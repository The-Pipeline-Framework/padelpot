# How PadelPot uses TPF

## From FCIS to Typed Core / Smart Shell

TPF is inspired by [Functional Core / Imperative Shell (FCIS)](https://pipelineframework.org/architecture/fcis): business decisions have explicit inputs and outputs, and the surrounding shell connects them to external systems. Its [agentic capabilities](https://pipelineframework.org/value/ai-and-agentic-applications) extend that separation into **Typed Core / Smart Shell**. Model judgement can contribute a typed proposal within a flow whose contracts, authority boundaries and completion conditions remain explicit.

In PadelPot, the **typed core** includes generated domain contracts, validation and deterministic privacy, eligibility and participation rules. Gemma returns an `AgentDecision`; it cannot author trusted execution state or approve a human commitment. The **smart shell** coordinates model Queries, application Commands, nested execution, correlated owner responses and replay evidence. Application connectors own access to persona and match state; the framework manages the execution around those boundaries.

## Negotiation and human approval

The web chat is a channel adapter for a real local application. Every owner message uses an envelope with owner ID, message ID, text and, when replying, interaction ID and explicit decision. Future WhatsApp/Signal/email adapters would implement that same envelope and delivery contract. None is implemented here.

`pipeline.yaml` and its generated canonical domain contracts own execution. A new message admits one queue-async root. A nested `personal-agent` pipeline loads a versioned minimal actor snapshot through an application Query, makes one Gemma Query, validates the inert decision, and dispatches an application Command. The Command returns a typed continuation, owner review or final result. The existing self-recursive nested invocation advances to the next agent; it does not call PipelineExecutionService or create an extra root.

The model sees an explicit structured-output schema and inspectable instructions. TPF's completion envelope copies trusted state from input after validating the decision. Actor IDs, request IDs, versions, expiry and transcript are carried by the framework, not supplied by the model. There is no tool executor in the inference provider. Application code applies only the permitted structured action.

An owner review returns to the root. A regular `RequestReview` operation returns its immediate Review request; its `await:` modifier changes completion to an AnsweredReview after the interaction-api adapter admits a correlated owner response. The channel adapter validates the owner and combines the stored review with the answer before native completion; this avoids a released request-aware projector transport/domain mismatch. Privacy and participation authority are checked again by the subsequent Command. There is no Await step type. The release's inability to suspend a nested definition is accommodated by two explicit root completion checkpoints: clarification/review, then final review after clarified negotiation.

The sequence below follows a candidate's match request. Each model turn acts for one player; permitted terms pass to the next agent through trusted state. A clarification resumes negotiation before a second owner checkpoint. An agreeable proposal can go directly to participation approval.

```mermaid
sequenceDiagram
    actor Owner as Owner via web chat
    participant Channel as ChannelAdapter
    participant TPF as TPF execution
    participant App as BusinessPort via PadelConnector
    participant Store as DemoStore
    participant Gemma as Gemma inference

    Owner->>Channel: Message(ownerId, messageId, text)
    Channel->>TPF: executePipelineAsync(TurnState)
    TPF-->>Channel: Accepted execution ID
    Channel-->>Owner: Request accepted

    loop Bounded personal-agent turns
        TPF->>App: persona.snapshot Query
        App->>Store: Read actor preferences and match terms
        Store-->>App: Application-owned versioned state
        App-->>TPF: ModelInput with minimal player brief
        TPF->>Gemma: One Query with brief and permitted peer terms
        Gemma-->>TPF: Structured AgentDecision
        TPF->>TPF: ValidateTurn with trusted TurnState
        TPF->>App: turn.apply Command
        App->>App: Check disclosure, constraints, expiry and turn limit
        App-->>TPF: Again, Review or Result
        Note over TPF,App: Again continues within the same root.<br/>Review returns to the root.
    end

    opt Owner review required
        TPF->>TPF: RequestReview operation with Await completion
        Owner->>Channel: Poll private conversation
        Channel->>TPF: Query pending interactions
        TPF-->>Channel: Review and interaction ID
        Channel-->>Owner: Clarification or participation approval
        Owner->>Channel: Correlated explicit choice
        Channel->>Channel: Validate owner and construct AnsweredReview
        Channel->>TPF: completeAwaitInteraction (same root)
        TPF->>App: human.apply Command
        alt Clarification or counterproposal
            App-->>TPF: Again with the owner response
            Note over TPF,Gemma: Repeat bounded nested negotiation.<br/>RequestFinalReview awaits the final owner choice.<br/>human.final rechecks authority and capacity in the same root.
        else Participation approval
            App->>App: Check owner, expiry, versions, blocks and capacity
            App->>Store: Record participation and save if valid
            App-->>TPF: Result
        else Rejection or invalid authority
            App-->>TPF: Result without commitment
        end
    end

    Owner->>Channel: Poll private conversation and match
    Channel->>Store: Read owner messages and public match state
    Store-->>Channel: Current outcome
    Channel-->>Owner: Update conversation
```

## Application classes and state ownership

The organizer's match aggregate is the single authoritative capacity record. The application port serializes its local mutations and atomically persists its JSON snapshot. Persona state is application-owned and survives restart. TPF's memory providers own pending execution, Await interaction and Command effect state while the process is alive. We do not claim recovery of those pending roots after restart. Native Query capture, Command duplicate handling and correlated completion provide their respective execution guarantees; these are different authorities.

This class diagram selects the boundary classes and persisted business state. `Snapshot`, `Player`, `Fact`, `Match` and `Intent` are nested classes in `DemoStore`. A persona is represented by a versioned `Player` and its facts; confirmed participation is stored in `Match.players`, keyed by owner ID with the agreed court side. The contracts module supplies `ApplicationPort`, `PadelConnector` and generated domain types; the app module supplies the runtime implementation.

```mermaid
classDiagram
    direction TB
    class ChannelAdapter {
        send(Message) Object
    }
    class PipelineExecutionService {
        <<TPF>>
        executePipelineAsync()
        queryPendingAwaitInteractions()
        completeAwaitInteraction()
    }
    class PadelConnector {
        operations()
    }
    class ApplicationPort {
        <<interface>>
        context(TurnState) ModelInput
        apply(ModelTurn) TurnOutcome
        answer(AnsweredReview) TurnOutcome
        answerFinal(AnsweredReview) Result
    }
    class BusinessPort {
        lock() Object
    }
    class Policy {
        disclosure(field, permission) String
        validFact(field, value) boolean
        bounded(TurnState) boolean
    }
    class DemoStore {
        snapshot() Snapshot
        player(id) Player
        save()
    }
    class Snapshot
    class Player {
        String id
        long version
        Set~String~ blocked
    }
    class Fact {
        String value
        String source
        String disclosure
    }
    class Match {
        String organizer
        String status
        int hour
        long version
        Map~String,String~ players
    }
    class Intent {
        String requestId
        String ownerId
        String matchId
        long deadline
        long ownerVersion
        long organizerVersion
        boolean cancelled
    }

    ChannelAdapter --> PipelineExecutionService : admit and resume
    ChannelAdapter --> BusinessPort : shared mutation lock
    ChannelAdapter --> DemoStore : owner conversations
    PadelConnector --> ApplicationPort : Query and Command delegation
    ApplicationPort <|.. BusinessPort
    BusinessPort ..> Policy : deterministic checks
    BusinessPort --> DemoStore : read and persist
    DemoStore *-- Snapshot
    Snapshot *-- "0..*" Player
    Snapshot *-- "0..*" Match
    Snapshot *-- "0..*" Intent
    Player *-- "0..*" Fact : preferences and provenance
    Match ..> Player : approved owner IDs, capacity four
    Intent ..> Player : owner and version
    Intent ..> Match : authorised opportunity
```

## Execution evidence and dependencies

The declared final E2E budget is five roots: one open request, three find requests, and one post-match feedback request. Owner clarifications, four participation approvals and the learning approval are resumes. Native file replay documents and their original topology/lifecycle events are authoritative execution evidence. The Demo Execution Dataset references those documents and includes permitted business events and public match outcome. It never manufactures a substitute TPF trace.

All TPF dependencies are pinned to 26.9.4, with matching source examined at `v26.9.4` in the upstream framework repository. Quarkus is pinned to 3.39.2. Domain, protobuf and transport adapters are generated with the released PipelineProtoGenerator, PipelineJavaDomainGenerator and annotation processor. Committed IDL files stabilize wire identities. The contracts Maven module is a compile-time library; the app is the only runtime process.

## Observed execution limitation

One live privacy run failed on recursive descent with “Recursive pipeline invocation adapter does not belong to the active parent invocation”. No private value or candidate commitment escaped. The same scenario completed after a process restart. Inspection of the available 26.10.1-SNAPSHOT runtime found the same parent-context guard; switching versions was not demonstrated to fix it, so the demo retains 26.9.4. This is an unresolved intermittent execution failure, not a privacy-policy bypass. The runner treats it as a failed recording, preserves diagnostics, and never silently retries inference or replaces it with fixture dialogue. No framework files were changed.
