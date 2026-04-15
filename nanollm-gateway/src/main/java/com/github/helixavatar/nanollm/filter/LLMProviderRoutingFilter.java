package com.github.helixavatar.nanollm.filter;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.addOriginalRequestUrl;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.isAlreadyRouted;

import com.github.helixavatar.nanollm.config.LLMProviderConfig;
import java.net.URI;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequest.Builder;
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
      // TODO support multiple providers
      String newPath = llmProviderConfig.getProvider().getFirst().getBaseUrl() + "/chat/completions";
      URI uri = URI.create(newPath);
      Builder mutate = request.mutate();
      mutate.uri(uri);
      mutate.header("Authorization", "Bearer " + llmProviderConfig.getProvider().getFirst().getApikey().getFirst());

      ServerHttpRequest newRequest = mutate.build();

      exchange.getAttributes().put(GATEWAY_REQUEST_URL_ATTR, newRequest.getURI());

      return chain.filter(exchange.mutate().request(newRequest).build());
    }
    else {
      return chain.filter(exchange);
    }
  }
}
