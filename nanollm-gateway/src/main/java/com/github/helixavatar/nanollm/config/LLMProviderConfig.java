package com.github.helixavatar.nanollm.config;

import java.util.Map;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "llm")
public class LLMProviderConfig {

  private Map<String, LLMProvider> provider;

}
