package com.github.helixavatar.nanollm;

import com.github.helixavatar.nanollm.filter.LLMProviderRoutingFilter;
import com.github.helixavatar.nanollm.filter.ModelRequestBodyRewriteFunction;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GatewayRoute {

  @Bean
  public RouteLocator routes(RouteLocatorBuilder builder, LLMProviderRoutingFilter llmProviderRoutingFilter, ModelRequestBodyRewriteFunction modelRequestBodyRewriteFunction) {
    return builder.routes()
        .route(
            "llm-route",
            r -> r.path("/v1/**")
                .filters(f -> f.modifyRequestBody(String.class, String.class, modelRequestBodyRewriteFunction)
                    .filter(llmProviderRoutingFilter)
                )
                .uri("https://abc.com/"))
        .build();
  }
}
