package com.github.helixavatar.nanollm.filter;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.github.helixavatar.nanollm.config.LLMProviderConfig;
import com.github.helixavatar.nanollm.utils.CollectionUtils;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.factory.rewrite.RewriteFunction;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class ModelRequestBodyRewriteFunction implements RewriteFunction<String, String> {

  @Autowired
  private LLMProviderConfig llmProviderConfig;

  @Override
  public Publisher<String> apply(ServerWebExchange exchange, String s) {
    String trim = s.trim();
    if (trim.startsWith("{") && trim.endsWith("}")) {
      JSONObject jsonObject = JSON.parseObject(trim);
      String model = jsonObject.getString("model");
      log.info("model: {}", model);
      exchange.getAttributes().put("ORIGINAL_REQUEST_MODEL", model);

      if (model.contains("/")) {
        model = replaceModel(exchange, model);
      }
      jsonObject.put("model", model);
      trim = jsonObject.toJSONString();
    }
    return Mono.just(trim);
  }

  private String replaceModel(ServerWebExchange exchange, String model) {
    String[] split = model.split("/");

    // TODO 此处可以根据不同的模型进行不同的处理
    String provider = split[0];
    model = split[1];

    exchange.getAttributes().put("ORIGINAL_MODEL_PROVIDER", provider);

    List<String> models = llmProviderConfig.getProvider().get(provider).getAlias().get(model);
    if (CollectionUtils.isNotEmpty(models)) {
      model = CollectionUtils.randomOne(models);
    }

    return model;
  }
}
