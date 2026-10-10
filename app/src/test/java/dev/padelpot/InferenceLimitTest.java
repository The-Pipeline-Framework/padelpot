package dev.padelpot;

import static org.junit.jupiter.api.Assertions.*;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.*;
import dev.langchain4j.model.chat.request.*;
import dev.langchain4j.model.chat.response.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class InferenceLimitTest {
  @Test
  void twoResumedRootsCannotInferConcurrently() throws Exception {
    AtomicInteger active = new AtomicInteger(), maximum = new AtomicInteger();
    ChatModel provider =
        new ChatModel() {
          public ChatResponse chat(ChatRequest request) {
            int n = active.incrementAndGet();
            maximum.accumulateAndGet(n, Math::max);
            try {
              Thread.sleep(20);
              return ChatResponse.builder().aiMessage(AiMessage.from("{}")).build();
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
              throw new IllegalStateException(e);
            } finally {
              active.decrementAndGet();
            }
          }
        };
    var first = new OpenRouterConnection.BoundedModel(provider);
    var second = new OpenRouterConnection.BoundedModel(provider);
    try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
      var calls = new ArrayList<Callable<ChatResponse>>();
      for (int i = 0; i < 8; i++) {
        var model = i % 2 == 0 ? first : second;
        calls.add(
            () ->
                model.chat(
                    ChatRequest.builder()
                        .messages(dev.langchain4j.data.message.UserMessage.from("synthetic"))
                        .build()));
      }
      for (var f : pool.invokeAll(calls)) assertNotNull(f.get());
    }
    assertEquals(1, maximum.get());
    assertEquals(0, active.get());
  }
}
