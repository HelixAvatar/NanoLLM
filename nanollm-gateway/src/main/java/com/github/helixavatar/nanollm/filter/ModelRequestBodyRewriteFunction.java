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
      String originModel = jsonObject.getString("model");
      log.debug("接收到请求参数 model: {}", originModel);
      exchange.getAttributes().put("ORIGINAL_REQUEST_MODEL", originModel);
      String model = replaceModel(exchange, originModel);
      jsonObject.put("model", model);
      trim = jsonObject.toJSONString();
      log.info("接收到 model: {} 替换后 model: {}", originModel, model);
      log.info("实际转发后请求参数: {}", trim);
    }
    return Mono.just(trim);
  }

  private String replaceModel(ServerWebExchange exchange, String model) {
    if (!model.contains("/")) {
      return model;
    }

    String[] split = model.split("/");

    String provider = split[0];
    model = split[1];

    exchange.getAttributes().put("ORIGINAL_MODEL_PROVIDER", provider);

    // TODO 此处应该增加供应商不存在的处理
    List<String> models = llmProviderConfig.getProvider().get(provider).getAlias().get(model);
    if (CollectionUtils.isNotEmpty(models)) {
      model = CollectionUtils.randomOne(models);
    }

    return model;
  }
}
