package dev.padelpot.connector;

import dev.padelpot.domain.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import java.util.*;
import java.util.concurrent.*;
import org.pipelineframework.connector.*;
import org.pipelineframework.blocks.chat.ChatState;

@ApplicationScoped
public class PadelConnector implements ConnectorProvider<PadelConnector.Config> {
  @Inject Instance<PadelOperations> application;

  public record Config(String fixture) {}

  public record OperationConfig(String label) {}

  public ConnectorProviderId id() {
    return ConnectorProviderId.of("padelpot.application");
  }

  public ConnectorProviderVersion version() {
    return new ConnectorProviderVersion(1, 0);
  }

  public Optional<ConnectorConfigSchema<Config>> configurationSchema() {
    return Optional.of(ConnectorConfigSchema.record(Config.class, "padelpot.application", 1));
  }

  public Collection<? extends ConnectorOperation> operations() {
    return List.of(new Load(this), new ChatContext(this),
        new CourtGuideLookup(this), new PublishChatReply(this), new ApplyOwnerAction(this),
        new SubmitPendingCounter(this),
        new Apply(this), new Answer(this), new FinalAnswer(this), new EraseHistory(this));
  }

  private static final class CourtGuideLookup
      implements QueryOperation<CourtGuideRequest, OperationConfig, CourtGuide> {
    private final PadelConnector p;

    CourtGuideLookup(PadelConnector p) { this.p = p; }

    public String id() { return "court.guide"; }

    public QueryCapabilities capabilities() { return QueryCapabilities.cacheable(); }

    public Optional<ConnectorConfigSchema<OperationConfig>> configurationSchema() {
      return Optional.of(ConnectorConfigSchema.record(OperationConfig.class, "padelpot.court.guide", 1));
    }

    public CompletionStage<QueryOutcome<CourtGuide>> query(
        QueryInvocation<CourtGuideRequest, OperationConfig, CourtGuide> invocation) {
      return CompletableFuture.completedFuture(
          new QueryOutcome.Found<>(p.application.get().courtGuide(invocation.input())));
    }
  }

  private static final class PublishChatReply
      implements CommandOperation<OwnerReply, OperationConfig, Result> {
    private final PadelConnector p;

    PublishChatReply(PadelConnector p) { this.p = p; }

    public String id() { return "chat.reply.publish"; }

    public Optional<ConnectorConfigSchema<OperationConfig>> configurationSchema() {
      return Optional.of(ConnectorConfigSchema.record(OperationConfig.class, "padelpot.chat.reply", 1));
    }

    public CompletionStage<CommandOutcome<Result>> dispatch(
        CommandInvocation<OwnerReply, OperationConfig> invocation) {
      return CompletableFuture.completedFuture(new CommandOutcome.Succeeded<>(
          p.application.get().acceptChatReply(invocation.input()), CommandConfirmation.none(), List.of()));
    }
  }

  private static final class ApplyOwnerAction
      implements CommandOperation<OwnerAction, OperationConfig, TurnOutcome> {
    private final PadelConnector p;

    ApplyOwnerAction(PadelConnector p) { this.p = p; }

    public String id() { return "owner.action.apply"; }

    public Optional<ConnectorConfigSchema<OperationConfig>> configurationSchema() {
      return Optional.of(ConnectorConfigSchema.record(OperationConfig.class, "padelpot.owner.action", 1));
    }

    public CompletionStage<CommandOutcome<TurnOutcome>> dispatch(
        CommandInvocation<OwnerAction, OperationConfig> invocation) {
      return CompletableFuture.completedFuture(new CommandOutcome.Succeeded<>(
          p.application.get().applyOwnerAction(invocation.input()), CommandConfirmation.none(), List.of()));
    }
  }

  private static final class SubmitPendingCounter
      implements CommandOperation<PendingCounter, OperationConfig, Result> {
    private final PadelConnector p;

    SubmitPendingCounter(PadelConnector p) { this.p = p; }

    public String id() { return "review.counter.submit"; }

    public Optional<ConnectorConfigSchema<OperationConfig>> configurationSchema() {
      return Optional.of(ConnectorConfigSchema.record(OperationConfig.class, "padelpot.review.counter", 1));
    }

    public CompletionStage<CommandOutcome<Result>> dispatch(
        CommandInvocation<PendingCounter, OperationConfig> invocation) {
      return p.application.get().submitPendingCounter(invocation.input())
          .thenApply(result -> new CommandOutcome.Succeeded<>(
              result, CommandConfirmation.none(), List.of()));
    }
  }

  private static final class ChatContext
      implements BlockingQueryOperation<ChatIngress, OperationConfig, ChatState> {
    private final PadelConnector p;

    ChatContext(PadelConnector p) { this.p = p; }

    public String id() { return "chat.context"; }

    public Optional<ConnectorConfigSchema<OperationConfig>> configurationSchema() {
      return Optional.of(ConnectorConfigSchema.record(OperationConfig.class, "padelpot.chat.context", 1));
    }

    public CompletionStage<QueryOutcome<ChatState>> query(
        QueryInvocation<ChatIngress, OperationConfig, ChatState> invocation) {
      return CompletableFuture.completedFuture(
          new QueryOutcome.Found<>(p.application.get().chatContext(invocation.input())));
    }
  }

  private static final class Load
      implements QueryOperation<TurnState, OperationConfig, ModelInput> {
    private final PadelConnector p;

    Load(PadelConnector p) {
      this.p = p;
    }

    public String id() {
      return "persona.snapshot";
    }

    public QueryCapabilities capabilities() {
      return QueryCapabilities.cacheable();
    }

    public Optional<ConnectorConfigSchema<OperationConfig>> configurationSchema() {
      return Optional.of(ConnectorConfigSchema.record(OperationConfig.class, "padelpot.load", 1));
    }

    public CompletionStage<QueryOutcome<ModelInput>> query(
        QueryInvocation<TurnState, OperationConfig, ModelInput> i) {
      return CompletableFuture.supplyAsync(
          () -> new QueryOutcome.Found<>(p.application.get().contextWithHistory(i.input())),
          Thread::startVirtualThread);
    }
  }

  private static final class Apply
      implements CommandOperation<ValidatedModelTurn, OperationConfig, TurnOutcome> {
    private final PadelConnector p;

    Apply(PadelConnector p) {
      this.p = p;
    }

    public String id() {
      return "turn.apply";
    }

    public Optional<ConnectorConfigSchema<OperationConfig>> configurationSchema() {
      return Optional.of(ConnectorConfigSchema.record(OperationConfig.class, "padelpot.apply", 1));
    }

    public CompletionStage<CommandOutcome<TurnOutcome>> dispatch(
        CommandInvocation<ValidatedModelTurn, OperationConfig> i) {
      return CompletableFuture.completedFuture(
          new CommandOutcome.Succeeded<>(
              p.application.get().apply(i.input()), CommandConfirmation.none(), List.of()));
    }
  }

  private static final class Answer
      implements CommandOperation<AnsweredReview, OperationConfig, OwnerInteraction> {
    private final PadelConnector p;

    Answer(PadelConnector p) {
      this.p = p;
    }

    public String id() {
      return "human.apply";
    }

    public Optional<ConnectorConfigSchema<OperationConfig>> configurationSchema() {
      return Optional.of(ConnectorConfigSchema.record(OperationConfig.class, "padelpot.answer", 1));
    }

    public CompletionStage<CommandOutcome<OwnerInteraction>> dispatch(
        CommandInvocation<AnsweredReview, OperationConfig> i) {
      return CompletableFuture.completedFuture(
          new CommandOutcome.Succeeded<>(
              p.application.get().answer(i.input()), CommandConfirmation.none(), List.of()));
    }
  }

  private static final class FinalAnswer
      implements CommandOperation<AnsweredReview, OperationConfig, OwnerInteraction> {
    private final PadelConnector p;

    FinalAnswer(PadelConnector p) {
      this.p = p;
    }

    public String id() {
      return "human.final";
    }

    public Optional<ConnectorConfigSchema<OperationConfig>> configurationSchema() {
      return Optional.of(ConnectorConfigSchema.record(OperationConfig.class, "padelpot.final", 1));
    }

    public CompletionStage<CommandOutcome<OwnerInteraction>> dispatch(
        CommandInvocation<AnsweredReview, OperationConfig> i) {
      OwnerInteraction r = p.application.get().answerFinal(i.input());
      return CompletableFuture.completedFuture(
          new CommandOutcome.Succeeded<>(r, CommandConfirmation.none(), List.of()));
    }
  }

  private static final class EraseHistory
      implements CommandOperation<HistoryEraseRequest, OperationConfig, Result> {
    private final PadelConnector p;

    EraseHistory(PadelConnector p) {
      this.p = p;
    }

    public String id() {
      return "history.erase";
    }

    public Optional<ConnectorConfigSchema<OperationConfig>> configurationSchema() {
      return Optional.of(ConnectorConfigSchema.record(OperationConfig.class, "padelpot.history.erase", 1));
    }

    public CompletionStage<CommandOutcome<Result>> dispatch(
        CommandInvocation<HistoryEraseRequest, OperationConfig> invocation) {
      return CompletableFuture.supplyAsync(
          () -> new CommandOutcome.Succeeded<>(
              p.application.get().eraseHistory(invocation.input()),
              CommandConfirmation.none(), List.of()),
          Thread::startVirtualThread);
    }
  }
}
