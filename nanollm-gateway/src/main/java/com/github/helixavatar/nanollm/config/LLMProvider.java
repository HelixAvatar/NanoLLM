package com.github.helixavatar.nanollm.config;

import java.util.HashMap;
import java.util.Map;
import lombok.Data;

import java.util.List;

@Data
public class LLMProvider {
    private String baseUrl;
    private List<String> apikey;
    private Map<String, List<String>> alias = new HashMap<>();
}
