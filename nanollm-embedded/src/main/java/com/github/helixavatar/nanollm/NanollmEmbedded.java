package com.github.helixavatar.nanollm;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.RequestOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.yaml.snakeyaml.Yaml;

/**
 * 轻量内嵌 HTTP 入口，行为对齐 {@code nanollm-gateway} 中与 OpenAI 兼容相关的部分：
 * <ul>
 *   <li>仅处理 {@code POST /v1/chat/completions}</li>
 *   <li>解析 JSON body 中的 {@code model}；若为 {@code provider/模型} 则记录 provider，并按配置将逻辑模型映射为上游真实模型名</li>
 *   <li>将请求转发到对应 provider 的 {@code baseUrl + "/chat/completions"}，并设置 {@code Authorization: Bearer &lt;随机 apikey&gt;}</li>
 *   <li>provider 未配置时返回 400 与 {@code {"error":"Model provider not found"}}</li>
 * </ul>
 */
public class NanollmEmbedded {

  private static final Logger LOG = Logger.getLogger(NanollmEmbedded.class.getName());

  private static final String DEFAULT_CONFIG_RESOURCE = "nanollm-embedded.yml";

  private static final Pattern ENV_REF = Pattern.compile("^\\$\\{([^}:]+)(?::-(.*))?}$");

  private Vertx vertx;
  private HttpServer server;
  private HttpClient upstreamClient;
  private Map<String, LlmProvider> providers = Map.of();

  /**
   * 在指定端口启动；若 classpath 上存在 {@value #DEFAULT_CONFIG_RESOURCE} 则加载其中的 {@code llm.provider} 配置。
   */
  public void start(int port) {
    InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(DEFAULT_CONFIG_RESOURCE);
    if (in == null) {
      LOG.warning("Classpath 上未找到 " + DEFAULT_CONFIG_RESOURCE + "，将以空 provider 映射启动");
      start(port, Map.of());
    }
    else {
      try (InputStream stream = in) {
        start(port, loadProvidersFromYaml(stream));
      }
      catch (Exception e) {
        throw new IllegalStateException("加载 " + DEFAULT_CONFIG_RESOURCE + " 失败", e);
      }
    }
  }

  /**
   * 在指定端口启动并使用给定的 provider 映射（键为请求 model 中的 provider 段，与网关一致）。
   */
  public void start(int port, Map<String, LlmProvider> providerMap) {
    Objects.requireNonNull(providerMap, "providerMap");
    if (vertx != null) {
      throw new IllegalStateException("已经启动");
    }
    this.providers = Collections.unmodifiableMap(new HashMap<>(providerMap));
    this.vertx = Vertx.vertx();
    this.upstreamClient = vertx.createHttpClient(
        new HttpClientOptions()
            .setSsl(true)
            .setTrustAll(true)
            .setIdleTimeout(30));
    Router router = Router.router(vertx);
    router.route().handler(BodyHandler.create().setBodyLimit(64L * 1024 * 1024));
    router.post("/v1/chat/completions").handler(this::handleChatCompletions);

    this.server = vertx.createHttpServer().requestHandler(router);
    Future.await(server.listen(port));
    LOG.info(() -> "NanoLLM Embedded 已监听端口 " + port);
  }

  /**
   * 停止 HTTP 服务并关闭 Vert.x 实例。
   */
  public Future<Void> stop() {
    if (vertx == null) {
      return Future.succeededFuture();
    }
    Future<Void> closeServer = server != null ? server.close() : Future.succeededFuture();
    return closeServer.compose(v -> {
      Vertx vRef = vertx;
      vertx = null;
      server = null;
      upstreamClient = null;
      // 仅关闭 Vert.x：会一并释放由此 Vert.x 创建的 HttpClient，避免与显式 HttpClient.close 竞态导致挂起
      return vRef.close();
    });
  }

  static Map<String, LlmProvider> loadProvidersFromYaml(InputStream yamlStream) {
    Yaml yaml = new Yaml();
    Object root = yaml.load(yamlStream);
    if (!(root instanceof Map<?, ?> map)) {
      return Map.of();
    }
    Object llm = map.get("llm");
    if (!(llm instanceof Map<?, ?> llmMap)) {
      return Map.of();
    }
    Object providerObj = llmMap.get("provider");
    if (!(providerObj instanceof Map<?, ?> providerRoot)) {
      return Map.of();
    }
    Map<String, LlmProvider> out = new HashMap<>();
    for (Map.Entry<?, ?> e : providerRoot.entrySet()) {
      if (e.getKey() == null || !(e.getValue() instanceof Map<?, ?> cfg)) {
        continue;
      }
      String name = String.valueOf(e.getKey());
      String baseUrl = stringVal(cfg, "baseUrl", "base-url");
      List<String> keys = stringList(cfg, "apikey", "apikeys", "api-keys");
      if (baseUrl == null || keys.isEmpty()) {
        continue;
      }
      LlmProvider p = new LlmProvider().baseUrl(baseUrl).apiKeys(keys);
      Object aliasObj = cfg.get("alias");
      if (aliasObj instanceof Map<?, ?> aliasMap) {
        for (Map.Entry<?, ?> ae : aliasMap.entrySet()) {
          if (ae.getKey() == null) {
            continue;
          }
          String logical = String.valueOf(ae.getKey());
          List<String> targets = new ArrayList<>();
          if (ae.getValue() instanceof List<?> list) {
            for (Object o : list) {
              if (o != null) {
                targets.add(expandEnv(String.valueOf(o)));
              }
            }
          }
          else if (ae.getValue() != null) {
            targets.add(expandEnv(String.valueOf(ae.getValue())));
          }
          if (!targets.isEmpty()) {
            p.alias(logical, targets);
          }
        }
      }
      out.put(name, p);
    }
    return out;
  }

  private void handleChatCompletions(RoutingContext ctx) {
    String rawBody = ctx.body().asString();
    if (rawBody == null) {
      rawBody = "";
    }
    String trimmed = rawBody.trim();
    RewriteResult rewrite = rewriteRequestBody(trimmed, providers);
    if (rewrite.providerKey == null) {
      respondJsonError(ctx, 400, "Model provider not found");
      return;
    }
    LlmProvider provider = providers.get(rewrite.providerKey);
    if (provider == null || provider.baseUrl() == null || provider.apiKeys().isEmpty()) {
      respondJsonError(ctx, 400, "Model provider not found");
      return;
    }
    String upstreamUrl = joinBaseAndPath(provider.baseUrl(), "/chat/completions");
    URI uri;
    try {
      uri = URI.create(upstreamUrl);
    }
    catch (IllegalArgumentException ex) {
      LOG.log(Level.WARNING, "非法 baseUrl: " + provider.baseUrl(), ex);
      respondJsonError(ctx, 500, "Invalid provider baseUrl");
      return;
    }
    boolean ssl = "https".equalsIgnoreCase(uri.getScheme());
    int upstreamPort = uri.getPort() > 0 ? uri.getPort() : (ssl ? 443 : 80);
    String host = uri.getHost();
    if (host == null || host.isEmpty()) {
      respondJsonError(ctx, 500, "Invalid provider baseUrl host");
      return;
    }
    String pathWithQuery = uri.getRawPath();
    if (pathWithQuery == null || pathWithQuery.isEmpty()) {
      pathWithQuery = "/";
    }
    if (uri.getRawQuery() != null && !uri.getRawQuery().isEmpty()) {
      pathWithQuery = pathWithQuery + "?" + uri.getRawQuery();
    }
    String incomingQuery = ctx.request().query();
    if (incomingQuery != null && !incomingQuery.isEmpty()) {
      pathWithQuery = pathWithQuery + (pathWithQuery.contains("?") ? "&" : "?") + incomingQuery;
    }

    String bearer = randomOne(provider.apiKeys());
    HttpMethod method = ctx.request().method();
    RequestOptions opts = new RequestOptions()
        .setMethod(method)
        .setHost(host)
        .setPort(upstreamPort)
        .setSsl(ssl)
        .setURI(pathWithQuery);

    upstreamClient.request(opts).onComplete(reqAr -> {
      if (reqAr.failed()) {
        LOG.log(Level.WARNING, "创建上游请求失败", reqAr.cause());
        if (!ctx.response().ended()) {
          ctx.response().setStatusCode(502).end();
        }
        return;
      }
      HttpClientRequest upstreamReq = reqAr.result();
      upstreamReq.putHeader(HttpHeaders.HOST, host);
      upstreamReq.putHeader(HttpHeaders.AUTHORIZATION, "Bearer " + bearer);
      copyClientRequestHeaders(ctx, upstreamReq);
      upstreamReq.setChunked(true);

      upstreamReq.send(Buffer.buffer(rewrite.bodyUtf8)).onComplete(sendAr -> {
        if (sendAr.failed()) {
          LOG.log(Level.WARNING, "转发上游失败", sendAr.cause());
          if (!ctx.response().ended()) {
            ctx.response().setStatusCode(502).end();
          }
          return;
        }
        HttpClientResponse upstreamResp = sendAr.result();
        ctx.response().setStatusCode(upstreamResp.statusCode());
        upstreamResp.headers().forEach(h -> {
          if (isHopByHopHeader(h.getKey())) {
            return;
          }
          ctx.response().putHeader(h.getKey(), h.getValue());
        });
        ctx.response().setChunked(true);
        upstreamResp.handler(ctx.response()::write);
        upstreamResp.endHandler(v -> ctx.response().end());
        upstreamResp.exceptionHandler(err -> {
          if (!ctx.response().ended()) {
            ctx.response().reset();
            ctx.response().setStatusCode(502).end();
          }
        });
      });
    });
  }

  private static void copyClientRequestHeaders(RoutingContext ctx, HttpClientRequest upstreamReq) {
    for (String name : ctx.request().headers().names()) {
      String lower = name.toLowerCase(Locale.ROOT);
      if ("host".equals(lower)
          || "content-length".equals(lower)
          || "authorization".equals(lower)
          || "connection".equals(lower)
          || "transfer-encoding".equals(lower)) {
        continue;
      }
      upstreamReq.putHeader(name, ctx.request().getHeader(name));
    }
    String contentType = ctx.request().getHeader(HttpHeaders.CONTENT_TYPE);
    if (contentType != null) {
      upstreamReq.putHeader(HttpHeaders.CONTENT_TYPE, contentType);
    }
  }

  private static boolean isHopByHopHeader(String name) {
    if (name == null) {
      return true;
    }
    String h = name.toLowerCase(Locale.ROOT);
    return "connection".equals(h)
        || "keep-alive".equals(h)
        || "proxy-authenticate".equals(h)
        || "proxy-authorization".equals(h)
        || "te".equals(h)
        || "trailer".equals(h)
        || "transfer-encoding".equals(h)
        || "upgrade".equals(h);
  }

  /**
   * 等价于网关 {@code ModelRequestBodyRewriteFunction} + 后续路由阶段对 provider 的解析所需信息：
   * {@code model} 无 {@code /} 时不设置 provider；含 {@code /} 时与网关一致使用 {@code String.split("/")} 取前两段作为 provider 与逻辑模型名。
   */
  static RewriteResult rewriteRequestBody(String trimmedBody, Map<String, LlmProvider> providers) {
    RewriteResult r = new RewriteResult();
    r.bodyUtf8 = trimmedBody;
    if (!trimmedBody.startsWith("{") || !trimmedBody.endsWith("}")) {
      return r;
    }
    JSONObject json = JSON.parseObject(trimmedBody);
    String originModel = json.getString("model");
    if (originModel == null) {
      r.bodyUtf8 = json.toJSONString();
      return r;
    }
    if (!originModel.contains("/")) {
      json.put("model", originModel);
      r.bodyUtf8 = json.toJSONString();
      return r;
    }
    String[] split = originModel.split("/");
    if (split.length < 2) {
      r.bodyUtf8 = json.toJSONString();
      return r;
    }
    String providerKey = split[0];
    String logicalModel = split[1];
    r.providerKey = providerKey;
    LlmProvider p = providers.get(providerKey);
    String resolvedModel = logicalModel;
    if (p != null) {
      List<String> targets = p.alias().get(logicalModel);
      if (targets != null && !targets.isEmpty()) {
        resolvedModel = randomOne(targets);
      }
    }
    json.put("model", resolvedModel);
    r.bodyUtf8 = json.toJSONString();
    return r;
  }

  private void respondJsonError(RoutingContext ctx, int status, String message) {
    JSONObject err = new JSONObject();
    err.put("error", message);
    ctx.response()
        .setStatusCode(status)
        .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
        .putHeader(HttpHeaders.CONNECTION, "close")
        .end(err.toJSONString());
  }

  static String joinBaseAndPath(String baseUrl, String suffixPath) {
    if (baseUrl == null) {
      return suffixPath;
    }
    if (baseUrl.endsWith("/") && suffixPath.startsWith("/")) {
      return baseUrl.substring(0, baseUrl.length() - 1) + suffixPath;
    }
    if (!baseUrl.endsWith("/") && !suffixPath.startsWith("/")) {
      return baseUrl + "/" + suffixPath;
    }
    return baseUrl + suffixPath;
  }

  private static String stringVal(Map<?, ?> map, String... keys) {
    for (String k : keys) {
      Object v = map.get(k);
      if (v instanceof String s && !s.isEmpty()) {
        return expandEnv(s);
      }
    }
    return null;
  }

  private static List<String> stringList(Map<?, ?> map, String... keys) {
    for (String k : keys) {
      Object v = map.get(k);
      if (v instanceof List<?> list) {
        List<String> out = new ArrayList<>();
        for (Object o : list) {
          if (o != null) {
            out.add(expandEnv(String.valueOf(o)));
          }
        }
        if (!out.isEmpty()) {
          return out;
        }
      }
    }
    return List.of();
  }

  static String expandEnv(String raw) {
    Matcher m = ENV_REF.matcher(raw);
    if (m.matches()) {
      String key = m.group(1);
      String def = m.group(2);
      String val = System.getenv(key);
      if (val == null || val.isEmpty()) {
        return def != null ? def : raw;
      }
      return val;
    }
    return raw;
  }

  static <T> T randomOne(List<T> list) {
    return list.get(ThreadLocalRandom.current().nextInt(list.size()));
  }

  static final class RewriteResult {
    String bodyUtf8;
    String providerKey;
  }

  /**
   * 与网关 {@code LLMProvider} 对应的配置对象（由 YAML 或调用方自行组装）。
   */
  public static final class LlmProvider {
    private String baseUrl;
    private List<String> apiKeys = List.of();
    private final Map<String, List<String>> alias = new HashMap<>();

    public LlmProvider baseUrl(String baseUrl) {
      this.baseUrl = baseUrl;
      return this;
    }

    public LlmProvider apiKeys(List<String> apiKeys) {
      this.apiKeys = apiKeys == null ? List.of() : List.copyOf(apiKeys);
      return this;
    }

    public LlmProvider alias(String logical, List<String> upstreamNames) {
      if (logical != null && upstreamNames != null && !upstreamNames.isEmpty()) {
        this.alias.put(logical, List.copyOf(upstreamNames));
      }
      return this;
    }

    public String baseUrl() {
      return baseUrl;
    }

    public List<String> apiKeys() {
      return apiKeys;
    }

    public Map<String, List<String>> alias() {
      return Collections.unmodifiableMap(alias);
    }
  }
}
