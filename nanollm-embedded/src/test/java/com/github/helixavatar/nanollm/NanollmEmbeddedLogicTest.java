package com.github.helixavatar.nanollm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 覆盖与网关一致的纯逻辑：YAML 加载、model 重写、路径拼接、环境占位符。
 */
class NanollmEmbeddedLogicTest {

  @Test
  void joinBaseAndPath_normalizesSlashes() {
    assertEquals("https://x/v1/chat/completions",
        NanollmEmbedded.joinBaseAndPath("https://x/v1", "/chat/completions"));
    assertEquals("https://x/v1/chat/completions",
        NanollmEmbedded.joinBaseAndPath("https://x/v1/", "/chat/completions"));
    assertEquals("https://x/v1/chat/completions",
        NanollmEmbedded.joinBaseAndPath("https://x/v1", "chat/completions"));
  }

  @Test
  void expandEnv_literalAndDefault() {
    assertEquals("plain", NanollmEmbedded.expandEnv("plain"));
    assertEquals("fallback", NanollmEmbedded.expandEnv("${__NANOLLM_EMBEDDED_NO_SUCH_ENV__:-fallback}"));
  }

  @Test
  void loadProvidersFromYaml_readsKebabCaseAndAlias() throws Exception {
    try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream("sample-llm.yml")) {
      assertNotNull(in);
      Map<String, NanollmEmbedded.LlmProvider> map = NanollmEmbedded.loadProvidersFromYaml(in);
      assertEquals(2, map.size());
      NanollmEmbedded.LlmProvider ds = map.get("dashscope");
      assertNotNull(ds);
      assertTrue(ds.baseUrl().contains("dashscope"));
      assertEquals(2, ds.apiKeys().size());
      assertEquals(List.of("kimi-k2.5"), ds.alias().get("kimi-k2"));
      NanollmEmbedded.LlmProvider min = map.get("minimal");
      assertNotNull(min);
      assertEquals("http://127.0.0.1:9/v1", min.baseUrl());
      assertEquals(List.of("key-only"), min.apiKeys());
    }
  }

  @Test
  void rewriteRequestBody_nonJson_leavesBodyAndNoProvider() {
    NanollmEmbedded.RewriteResult r = NanollmEmbedded.rewriteRequestBody("not json", Map.of());
    assertEquals("not json", r.bodyUtf8);
    assertNull(r.providerKey);
  }

  @Test
  void rewriteRequestBody_modelWithoutSlash_noProviderKey() {
    String body = "{\"model\":\"gpt-4\",\"messages\":[]}";
    NanollmEmbedded.RewriteResult r = NanollmEmbedded.rewriteRequestBody(body, Map.of());
    assertNull(r.providerKey);
    JSONObject json = JSON.parseObject(r.bodyUtf8);
    assertEquals("gpt-4", json.getString("model"));
  }

  @Test
  void rewriteRequestBody_providerModel_appliesSingleAliasTarget() {
    NanollmEmbedded.LlmProvider p = new NanollmEmbedded.LlmProvider()
        .baseUrl("https://u/v1")
        .apiKeys(List.of("k"))
        .alias("logical", List.of("upstream-only"));
    Map<String, NanollmEmbedded.LlmProvider> providers = Map.of("my", p);
    String body = "{\"model\":\"my/logical\"}";
    NanollmEmbedded.RewriteResult r = NanollmEmbedded.rewriteRequestBody(body, providers);
    assertEquals("my", r.providerKey);
    JSONObject json = JSON.parseObject(r.bodyUtf8);
    assertEquals("upstream-only", json.getString("model"));
  }

  @Test
  void rewriteRequestBody_unknownProvider_stillStripsProviderPrefix() {
    String body = "{\"model\":\"unknown/logical\"}";
    NanollmEmbedded.RewriteResult r = NanollmEmbedded.rewriteRequestBody(body, Map.of());
    assertEquals("unknown", r.providerKey);
    JSONObject json = JSON.parseObject(r.bodyUtf8);
    assertEquals("logical", json.getString("model"));
  }

  @Test
  void rewriteRequestBody_multiAlias_pickOneOfListed() {
    NanollmEmbedded.LlmProvider p = new NanollmEmbedded.LlmProvider()
        .baseUrl("https://u/v1")
        .apiKeys(List.of("k"))
        .alias("logical", List.of("a", "b", "c"));
    NanollmEmbedded.RewriteResult r = NanollmEmbedded.rewriteRequestBody("{\"model\":\"x/logical\"}", Map.of("x", p));
    assertEquals("x", r.providerKey);
    String model = JSON.parseObject(r.bodyUtf8).getString("model");
    assertTrue(List.of("a", "b", "c").contains(model));
  }

  @Test
  void rewriteRequestBody_splitMatchesGateway_twoSegmentsOnly() {
    NanollmEmbedded.RewriteResult r = NanollmEmbedded.rewriteRequestBody("{\"model\":\"p/a/extra\"}", Map.of());
    assertEquals("p", r.providerKey);
    assertEquals("a", JSON.parseObject(r.bodyUtf8).getString("model"));
  }

  @Test
  void startRejectsNullProviderMap() {
    assertThrows(NullPointerException.class, () -> new NanollmEmbedded().start(0, null));
  }
}
