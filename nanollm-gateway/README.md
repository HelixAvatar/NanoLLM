# NanoLLM Gateway

基于 **Spring Cloud Gateway（WebFlux）** 的轻量网关，用于把客户端发出的 **OpenAI 兼容** 请求（如 `/v1/chat/completions`）转发到上游 LLM 服务。`src/main/resources/application.yaml` 中已预置 **DashScope（阿里云百炼）**、**小米 MiMo**、**DeepSeek** 三类提供商的 Base URL 与密钥占位符，可按需启用或扩展。

## 功能概览

| 能力 | 说明 |
|------|------|
| 路径匹配 | 匹配 `/v1/**` |
| 请求体改写 | 解析 JSON 中的 `model` 字段，支持 `提供商前缀/别名或模型名` 形式；命中 `alias` 时映射为实际上游模型名 |
| 动态路由 | 对 `/v1/chat/completions` 将请求 URL 与 `Authorization` 改写为配置中的上游地址与 API Key |

处理顺序：`modifyRequestBody`（`ModelRequestBodyRewriteFunction`）→ `LLMProviderRoutingFilter`。

## 技术栈

- Java **25**
- Spring Boot **4.0.5**
- Spring Cloud **2025.1.1**（`spring-cloud-starter-gateway-server-webflux`）
- Fastjson2、Lombok

## 应用与运行参数（与 `application.yaml` 对齐）

| 配置项 | 值 | 说明 |
|--------|-----|------|
| `spring.application.name` | `nanollm-gateway` | Spring 应用名（日志与监控中可见） |
| `server.port` | `28080` | HTTP 监听端口 |
| `logging.level.org.springframework.cloud` | `DEBUG` | Cloud Gateway 调试日志；生产建议改为 `INFO` 或按需关闭 |

## 快速开始

### 环境要求

- JDK 25（与 `pom.xml` 中 `java.version` 一致）
- Maven 3.9+（或使用项目自带的 `./mvnw`）

### 配置环境变量

`application.yaml` 中为每个提供商使用占位符，请按实际调用的上游设置（未使用的可先填占位，避免 Spring 解析失败；若占位符未解析导致启动失败，需为所有引用变量提供值或从 YAML 中临时移除对应条目）。

| 环境变量 | 对应提供商 | 说明 |
|----------|------------|------|
| `DASHSCOPE_API_KEY` | DashScope | 阿里云百炼 / DashScope 兼容模式 API Key |
| `MIMO_API_KEY` | 小米 MiMo | `api.xiaomimimo.com` |
| `DEEPSEEK_API_KEY` | DeepSeek | `api.deepseek.ai` |

示例：

```bash
export DASHSCOPE_API_KEY="你的_DashScope_Key"
export MIMO_API_KEY="你的_MiMo_Key"
export DEEPSEEK_API_KEY="你的_DeepSeek_Key"
```

### 运行

```bash
cd nanollm-gateway
./mvnw spring-boot:run
```

服务根地址：`http://127.0.0.1:28080`（与 `server.port` 一致）。

### 调用示例

请求体中的 `model` 建议使用 **`model-prefix` / 别名或模型名`**（与下表各条目的 `model-prefix` 一致）：

**DashScope + 别名**（`alias.xiaomi` 映射到列表首项 `tongyi-xiaomi-analysis-pro`）：

```bash
curl http://127.0.0.1:28080/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d '{
    "model": "dashscope/xiaomi",
    "messages": [{"role": "user", "content": "你好"}]
  }'
```

**小米 MiMo / DeepSeek**（配置中无 `alias` 时，`/` 后字符串通常直接作为上游 `model`，具体以上游 API 为准）：

```bash
# 示例：前缀与 application.yaml 中 model-prefix 一致
curl http://127.0.0.1:28080/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d '{"model": "xiaomimimo/你的上游模型名", "messages": [{"role":"user","content":"hi"}]}'

curl http://127.0.0.1:28080/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d '{"model": "deepseek/deepseek-chat", "messages": [{"role":"user","content":"hi"}]}'
```

说明：DashScope 下若别名未命中，则使用 `model` 中 `/` 后的原始字符串作为上游 `model`。

## 配置说明（`application.yaml`）

### 当前仓库中的完整示例

以下内容与 `src/main/resources/application.yaml` 保持一致，便于对照与复制：

```yaml
spring:
  application:
    name: alibaba-openai-gateway

server:
  port: 28080

logging:
  level:
    org.springframework.cloud: DEBUG

llm:
  provider:
    - type: dashscope
      model-prefix: dashscope
      base-url: https://dashscope.aliyuncs.com/compatible-mode/v1
      alias:
        xiaomi:
          - tongyi-xiaomi-analysis-pro
          - tongyi-xiaomi-analysis-flash
      apikey:
        - ${DASHSCOPE_API_KEY}
    - type: xiaomimimo
      model-prefix: xiaomimimo
      base-url: https://api.xiaomimimo.com/v1
      apikey:
        - ${MIMO_API_KEY}
    - type: deepseek
      model-prefix: deepseek
      base-url: https://api.deepseek.ai/v1
      apikey:
        - ${DEEPSEEK_API_KEY}
```

### 提供商一览

| `type` | `model-prefix` | `base-url` | `alias` | `apikey` 占位符 |
|--------|----------------|------------|---------|-----------------|
| `dashscope` | `dashscope` | `https://dashscope.aliyuncs.com/compatible-mode/v1` | `xiaomi` → 两个 Tongyi 模型 | `${DASHSCOPE_API_KEY}` |
| `xiaomimimo` | `xiaomimimo` | `https://api.xiaomimimo.com/v1` | 无 | `${MIMO_API_KEY}` |
| `deepseek` | `deepseek` | `https://api.deepseek.ai/v1` | 无 | `${DEEPSEEK_API_KEY}` |

### `llm.provider` 条目字段说明

| 字段 | 含义 |
|------|------|
| `type` | 提供商类型标识（便于阅读与后续逻辑扩展） |
| `model-prefix` | 与请求体 `model` 中 **`前缀/`** 的前缀一致时，用于区分走哪一家上游（与实现演进对齐） |
| `base-url` | 上游 OpenAI 兼容 API 的 Base URL；路由过滤器会拼接 **`/chat/completions`** |
| `alias` | 可选。仅 **DashScope** 示例中配置：`别名` → 实际上游模型名列表；命中时使用列表 **第一个** |
| `apikey` | API Key 列表；路由过滤器当前使用各逻辑分支中的 **第一个** 作为 `Authorization: Bearer ...` |

## 项目结构

```
src/main/java/com/github/helixavatar/nanollm/
├── NanoLLMGatewayApplication.java   # 启动类
├── GatewayRoute.java                # 路由定义：/v1/** + 过滤器链
├── config/
│   ├── LLMProviderConfig.java       # @ConfigurationProperties("llm")
│   └── LLMProvider.java             # 单条提供商配置模型
└── filter/
    ├── ModelRequestBodyRewriteFunction.java  # 改写 JSON body 中的 model
    └── LLMProviderRoutingFilter.java         # 设置上游 URI 与 Authorization
```

## 测试

```bash
./mvnw test
```

当前包含 Spring 上下文加载测试（`NanoLLMGatewayApplicationTests`）。

## 实现状态与注意事项

- 路由 Bean 中 `uri("https://abc.com/")` 为占位；**实际转发地址** 由 `LLMProviderRoutingFilter` 写入 `GATEWAY_REQUEST_URL_ATTR` 完成。
- 仅对路径 **`/v1/chat/completions`** 执行上述 URL 与鉴权改写；其它 `/v1/**` 请求仍会经过 body 改写过滤器，但可能按默认链路转发。
- **多提供商配置与代码行为**：YAML 中虽已列出 DashScope、MiMo、DeepSeek，但 `LLMProviderRoutingFilter` / `ModelRequestBodyRewriteFunction` 中仍存在 **TODO**，当前实现仍大量依赖 **`llm.provider` 列表的第一项**（例如 `getFirst()`）解析别名与路由；要让 `xiaomimimo/`、`deepseek/` 自动对应各自 `base-url` 与 `apikey`，需在过滤器中按 `model` 前缀匹配配置项并完成联调。
- 日志默认对 `org.springframework.cloud` 为 **DEBUG**，生产环境建议按需下调。

## 许可证

以父仓库 [NanoLLM](https://github.com/HelixAvatar/NanoLLM) 的许可证为准（若子模块未单独声明）。
