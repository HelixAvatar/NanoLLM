# NanoLLM Gateway

基于 **Spring Cloud Gateway（WebFlux）** 的轻量网关，用于把客户端发出的 **OpenAI 兼容** 请求（如 `/v1/chat/completions`）转发到上游 LLM 服务。当前示例配置面向 **阿里云百炼 DashScope** 的兼容模式接口。

## 功能概览

| 能力 | 说明 |
|------|------|
| 路径匹配 | 匹配 `/v1/**` |
| 请求体改写 | 解析 JSON 中的 `model` 字段，支持 `提供商前缀/别名` 形式并映射为实际上游模型名 |
| 动态路由 | 对 `/v1/chat/completions` 将请求 URL 与 `Authorization` 改写为配置中的上游地址与 API Key |

处理顺序：`modifyRequestBody`（`ModelRequestBodyRewriteFunction`）→ `LLMProviderRoutingFilter`。

## 技术栈

- Java **25**
- Spring Boot **4.0.5**
- Spring Cloud **2025.1.1**（`spring-cloud-starter-gateway-server-webflux`）
- Fastjson2、Lombok

## 快速开始

### 环境要求

- JDK 25（与 `pom.xml` 中 `java.version` 一致）
- Maven 3.9+（或使用项目自带的 `./mvnw`）

### 配置环境变量

DashScope API Key 通过配置占位符注入，启动前请设置：

```bash
export DASHSCOPE_API_KEY="你的_API_Key"
```

### 运行

```bash
cd nanollm-gateway
./mvnw spring-boot:run
```

默认监听端口见 `src/main/resources/application.yaml`（当前为 **28080**）。

### 调用示例

在请求体中使用带前缀的模型名（前缀后的段若在 `alias` 中配置，会被替换为上游真实模型名）：

```bash
curl http://127.0.0.1:28080/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d '{
    "model": "dashscope/xiaomi",
    "messages": [{"role": "user", "content": "你好"}]
  }'
```

说明：`xiaomi` 在示例配置里映射到 `tongyi-xiaomi-analysis-pro`（取列表首项）。若别名未命中，则使用 `model` 中 `/` 后的原始字符串作为上游 `model`。

## 配置说明（`application.yaml`）

在 `llm.provider` 下可配置多个提供商条目（当前路由逻辑中仍以 **列表第一项** 为主，多提供商精细化路由为后续扩展点）。

常用字段：

| 字段 | 含义 |
|------|------|
| `type` | 提供商类型标识（预留） |
| `model-prefix` | 与请求体中 `model` 的 `前缀/模型` 前缀对应（改写逻辑中目前通过 `alias` 键解析，可与业务约定对齐） |
| `base-url` | 上游兼容 OpenAI 的 Base URL（不含路径时由过滤器拼接 `/chat/completions`） |
| `alias` | 别名 → 实际上游模型名列表的映射；命中时使用列表 **第一个** 模型名 |
| `apikey` | API Key 列表；路由过滤器使用 **第一个** 作为 `Authorization: Bearer ...` |

示例片段：

```yaml
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
```

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
- 代码中标注了 **TODO**：多提供商选择、`model-prefix` 与 `split[0]` 的严格校验等可继续完善。
- 日志默认对 `org.springframework.cloud` 为 **DEBUG**，生产环境建议按需下调。

## 许可证

以父仓库 [NanoLLM](https://github.com/HelixAvatar/NanoLLM) 的许可证为准（若子模块未单独声明）。
