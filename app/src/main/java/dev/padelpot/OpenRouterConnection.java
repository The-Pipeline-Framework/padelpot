package dev.padelpot;

import dev.langchain4j.model.openai.OpenAiChatModel;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.*;
import java.util.concurrent.*;
import org.pipelineframework.connector.*;
import org.pipelineframework.connector.llm.langchain4j.AuthenticatedOpenAiCompatibleConnection;

/** Credentials and fail-closed routing belong to the host, outside authored pipeline values. */
@ApplicationScoped
public class OpenRouterConnection implements ConnectionResolver {
  public <C extends ResolvedConnection> CompletionStage<C> resolve(
      ConnectionResolutionRequest<C> r) {
    boolean local = r.reference().value().equals("padelpot-ollama");
    if (!local && !r.reference().value().equals("padelpot-openrouter"))
      return CompletableFuture.failedFuture(
          new IllegalArgumentException("Unknown model connection"));
    String key = local ? "ollama" : System.getenv("OPENROUTER_API_KEY");
    if (key == null || key.isBlank())
      return CompletableFuture.failedFuture(
          new IllegalStateException(
              "OPENROUTER_API_KEY is required; no provider fallback is allowed"));
    if (!r.connectionType().equals(AuthenticatedOpenAiCompatibleConnection.class))
      return CompletableFuture.failedFuture(
          new IllegalArgumentException("Unsupported model connection"));
    AuthenticatedOpenAiCompatibleConnection c =
        new AuthenticatedOpenAiCompatibleConnection(
            config ->
                new BoundedModel(
                    OpenAiChatModel.builder()
                        .apiKey(key)
                        .baseUrl(config.baseUrl())
                        .modelName(config.modelName())
                        .timeout(config.timeout())
                        .maxRetries(config.maxRetries())
                        .strictJsonSchema(config.strictJsonSchema())
                        .temperature(0.2)
                        .maxTokens(600)
                        .logRequests(false)
                        .logResponses(false)
                        .store(false)
                        .customParameters(
                            local
                                ? Map.of("reasoning_effort", "none")
                                : Map.of(
                                    "provider",
                                    Map.of(
                                        "zdr",
                                        true,
                                        "data_collection",
                                        "deny",
                                        "allow_fallbacks",
                                        false,
                                        "require_parameters",
                                        true)))
                        .build()));
    return CompletableFuture.completedFuture(r.connectionType().cast(c));
  }

  /** Process-wide limit also covers replies resuming two distinct roots at once. */
  static final class BoundedModel implements dev.langchain4j.model.chat.ChatModel {
    private static final Semaphore INFERENCE = new Semaphore(1, true);
    private final dev.langchain4j.model.chat.ChatModel delegate;

    BoundedModel(dev.langchain4j.model.chat.ChatModel delegate) {
      this.delegate = delegate;
    }

    public dev.langchain4j.model.chat.response.ChatResponse chat(
        dev.langchain4j.model.chat.request.ChatRequest request) {
      boolean admitted = false;
      try {
        admitted = INFERENCE.tryAcquire(60, TimeUnit.SECONDS);
        if (!admitted) throw new IllegalStateException("Inference capacity deadline exceeded");
        return delegate.chat(request);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("Inference interrupted", e);
      } finally {
        if (admitted) INFERENCE.release();
      }
    }

    public Set<dev.langchain4j.model.chat.Capability> supportedCapabilities() {
      return delegate.supportedCapabilities();
    }
  }
}
