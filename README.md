# NanoLLM
Multi-provider abstraction for LLMs including Alibaba bailian

## nanollm-gateway

Spring Cloud Gateway 子模块：对外暴露 OpenAI 兼容 `/v1/**`，将 `chat/completions` 等请求转发到 DashScope、MiMo、DeepSeek 等上游（具体行为与配置见 [`nanollm-gateway/README.md`](nanollm-gateway/README.md)）。
