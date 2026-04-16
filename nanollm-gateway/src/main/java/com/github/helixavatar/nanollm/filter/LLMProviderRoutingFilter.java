package com.github.helixavatar.nanollm.filter;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.addOriginalRequestUrl;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.isAlreadyRouted;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.github.helixavatar.nanollm.config.LLMProvider;
import com.github.helixavatar.nanollm.config.LLMProviderConfig;
import com.github.helixavatar.nanollm.utils.CollectionUtils;
import java.net.URI;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequest.Builder;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;


@Component
@Slf4j
public class LLMProviderRoutingFilter implements GatewayFilter, Ordered {

  /**
   * 这里的值比为 10100 - 1 其中，10100 是 LoadBalancerClientFilter 的 Order, 这里比其提前执行
   */
  public static final int ORDER = 10000 + 1;

  @Autowired
  private LLMProviderConfig llmProviderConfig;

  @Override
  public int getOrder() {
    return ORDER;
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    if (isAlreadyRouted(exchange)) {
      return chain.filter(exchange);
    }
    log.info("llmProviderConfig: {}", llmProviderConfig);


    ServerHttpRequest request = exchange.getRequest();
    addOriginalRequestUrl(exchange, request.getURI());
    String path = request.getURI().getRawPath();

    log.info("path: {}", path);

    if (path.startsWith("/v1/chat/completions")) {
      String provider = (String) exchange.getAttributes().get("ORIGINAL_MODEL_PROVIDER");
      LLMProvider llmProvider = null;
      if (provider != null) {
        llmProvider = llmProviderConfig.getProvider().get(provider);
      }

      if (llmProvider == null) {
        ServerHttpResponse response = exchange.getResponse();
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        response.setStatusCode(HttpStatus.BAD_REQUEST);

        JSONObject error = new JSONObject();
        error.put("error", "Model provider not found");
        DataBuffer bodyDataBuffer = response.bufferFactory().wrap(JSON.toJSONBytes(error));
        return response.writeWith(Mono.just(bodyDataBuffer));
      }

      String newPath = llmProvider.getBaseUrl() + "/chat/completions";
      URI uri = URI.create(newPath);
      Builder mutate = request.mutate();
      mutate.uri(uri);
      mutate.header("Authorization", "Bearer " + CollectionUtils.randomOne(llmProvider.getApikey()));

      ServerHttpRequest newRequest = mutate.build();
      exchange.getAttributes().put(GATEWAY_REQUEST_URL_ATTR, newRequest.getURI());

      return chain.filter(exchange.mutate().request(newRequest).build());
    }
    else {
      return chain.filter(exchange);
    }
  }
}
