package dev.padelpot.connector;

import dev.padelpot.domain.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import java.util.*;
import java.util.concurrent.*;
import org.pipelineframework.connector.*;

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
    return List.of(new Load(this), new Apply(this), new Answer(this), new FinalAnswer(this));
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
      return CompletableFuture.completedFuture(
          new QueryOutcome.Found<>(p.application.get().context(i.input())));
    }
  }

  private static final class Apply
      implements CommandOperation<ModelTurn, OperationConfig, TurnOutcome> {
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
        CommandInvocation<ModelTurn, OperationConfig> i) {
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
}
