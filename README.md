# NanoLLM
Multi-provider abstraction for LLMs including Alibaba bailian

## nanollm-gateway

Spring Cloud Gateway 子模块：对外暴露 OpenAI 兼容 `/v1/**`，将 `chat/completions` 等请求转发到 DashScope、MiMo、DeepSeek 等上游（具体行为与配置见 [`nanollm-gateway/README.md`](nanollm-gateway/README.md)）。

**使用方式：**

根据需要修改 `application.yaml` 配置, 默认已经配置了 kimi-k2、qwen3.6、xiaomi、qwen3.5、glm-5、MiniMax 对应模型别名，运行项目之后，可以使用兼容 openai 的方式，将 baseUrl 设置为 `http://127.0.0.1:28080/v1`(根据实际情况修改)


```shell
curl --request POST 'http://127.0.0.1:28080/v1/chat/completions' \
--header 'Content-Type: application/json' \
--header 'Accept: */*' \
--header 'Connection: keep-alive' \
--data-raw '{
    "model": "dashscope/qwen3.5",
    "messages": [
        {
            "role": "system",
            "content": "You are a helpful assistant."
        },
        {
            "role": "user",
            "content": "你是谁？"
        }
    ]
}'
```

