package com.github.helixavatar.nanollm;

import com.github.helixavatar.nanollm.NanollmEmbedded.LlmProvider;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class NanollmEmbeddedTest {

  @Test
  public void testNanollmEmbedded() {

    LlmProvider llmProvider = new LlmProvider();
    llmProvider.baseUrl("https://dashscope.aliyuncs.com/compatible-mode/v1");
    llmProvider.apiKeys(List.of(System.getenv("DASHSCOPE_API_KEY")));
    llmProvider.alias("kimi-k2", List.of("kimi-k2.5"));
    llmProvider.alias("qwen3.6", List.of("qwen3.6-plus-2026-04-02"));
    llmProvider.alias("xiaomi", List.of("tongyi-xiaomi-analysis-pro", "tongyi-xiaomi-analysis-flash"));
    llmProvider.alias("qwen3.5",
        List.of("qwen3.5-122b-a10b", "qwen3.5-flash", "qwen3.5-plus-2026-02-15", "qwen3.5-flash-2026-02-23",
            "qwen3.5-397b-a17b"));
    llmProvider.alias("glm-5", List.of("glm-5", "glm-5.1"));
    llmProvider.alias("MiniMax", List.of("MiniMax-M2.5", "MiniMax-M2.1"));

    new NanollmEmbedded().start(18080, Map.of("dashscope", llmProvider));

    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create("http://127.0.0.1:18080/v1/chat/completions"))
        .timeout(Duration.ofMinutes(2))
        .header("Content-Type", "application/json")
        .POST(BodyPublishers.ofString("{\"model\":\"dashscope/qwen3.5\",\"messages\":[{\"role\":\"system\",\"content\":\"You are a helpful assistant.\"},{\"role\":\"user\",\"content\":\"你是谁？\"}]}"))
        .build();

    try {
      HttpClient client = HttpClient.newHttpClient();
      var response = client.send(request, HttpResponse.BodyHandlers.ofString());
      System.out.println(response.body());
    } catch (Exception e) {
      e.printStackTrace();
    }

  }

}
