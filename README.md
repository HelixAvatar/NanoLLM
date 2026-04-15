# NanoLLM

面向多厂商 LLM 的轻量抽象：当前仓库提供 **Spring Cloud Gateway** 子模块 **nanollm-gateway**，对外暴露 **OpenAI 兼容** 的 `/v1/**` 接口，将 `chat/completions` 等请求改写并转发到 **DashScope（阿里云百炼）**、**小米 MiMo**、**DeepSeek** 等上游。

## 仓库结构

| 路径 | 说明 |
|------|------|
| [`nanollm-gateway/`](nanollm-gateway/) | 网关应用（Java 25、Spring Boot 4、Spring Cloud Gateway） |

## 环境要求

- **JDK 25**（与 `nanollm-gateway/pom.xml` 中 `java.version` 一致）
- **Maven 3.9+**，或直接使用子模块内的 `./mvnw`

## 快速开始

### 1. 配置 API Key

在运行前导出环境变量（未调用的厂商可先填占位，避免占位符无法解析）：

| 环境变量 | 用途 |
|----------|------|
| `DASHSCOPE_API_KEY` | DashScope / 百炼兼容模式 |
| `MIMO_API_KEY` | 小米 MiMo |
| `DEEPSEEK_API_KEY` | DeepSeek |

```bash
export DASHSCOPE_API_KEY="你的_Key"
export MIMO_API_KEY="你的_Key"
export DEEPSEEK_API_KEY="你的_Key"
```

### 2. 启动网关

```bash
cd nanollm-gateway
./mvnw spring-boot:run
```

默认监听：**http://127.0.0.1:28080**（见 `application.yaml` 中 `server.port`）。

### 3. 调用约定

请求体中的 `model` 使用 **`厂商键/别名或模型名`**：

- **`厂商键`** 与 `llm.provider` 下的配置键一致：`dashscope`、`xiaomimimo`、`deepseek`。
- 对 **DashScope**，`/` 后为 **别名** 时，会映射到 YAML 中 `alias` 下列表里的实际上游模型；若别名下配置了多个模型，实现会从列表中 **随机选取其一**。
- 对 **MiMo / DeepSeek**，若别名字典中无对应项，则 **`/` 后字符串直接作为上游 `model`**。

**当前默认配置中的 DashScope 别名（节选，完整见 `nanollm-gateway/src/main/resources/application.yaml`）：**

| 请求中 `model` 示例 | 含义 |
|---------------------|------|
| `dashscope/kimi-k2` | 映射到 `kimi-k2.5` 等 |
| `dashscope/qwen3.6` | 映射到 `qwen3.6-plus-2026-04-02` 等 |
| `dashscope/xiaomi` | Tongyi 小米相关模型候选 |
| `dashscope/qwen3.5` | Qwen3.5 系列多个候选之一 |
| `dashscope/glm-5` | GLM-5 / GLM-5.1 等 |
| `dashscope/MiniMax` | MiniMax-M2.5 / M2.1 等 |
| `xiaomimimo/<上游模型名>` | 走 MiMo `base-url` |
| `deepseek/deepseek-chat` | 走 DeepSeek `base-url` |

将 OpenAI SDK 或兼容客户端的 **base URL** 设为：`http://127.0.0.1:28080/v1`（端口按实际修改）。

### 4. `curl` 示例

```bash
curl --request POST 'http://127.0.0.1:28080/v1/chat/completions' \
  --header 'Content-Type: application/json' \
  --header 'Accept: */*' \
  --data-raw '{
    "model": "dashscope/qwen3.5",
    "messages": [
      {"role": "system", "content": "You are a helpful assistant."},
      {"role": "user", "content": "你是谁？"}
    ]
  }'
```

## 开发与测试

```bash
cd nanollm-gateway
./mvnw test
```

## 许可证

本仓库采用 **Apache License 2.0**，见根目录 [`LICENSE`](LICENSE)。
