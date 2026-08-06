# OpenAI 兼容适配层

将 AgentScope Java 智能体伪装成 OpenAI 模型，让 LobeChat、ChatGPT-Next-Web 等标准前端无缝接入。

---

## 架构

```
LobeChat
  │ POST /v1/chat/completions
  │ Authorization: Bearer sk-agentcar-2026-abc123
  │ {model: "Agent名", messages: [...], stream: true}
  ▼
ApiKeyInterceptor → 校验 Bearer Token
  ▼
OpenAiApiController
  │ 创建 SseEmitter(taskId)
  │ 创建 OpenAiFormatHook(taskId, model)
  │ 虚拟线程异步执行
  ▼
StatelessAgentDispatcher
  │ messages[] → Msg[]
  │ 历史消息通过 addMessage() 注入 Agent 记忆
  │ 最后一条消息通过 agent.call() 触发执行
  ▼
Agent 执行 (ReActAgent.call)
  │ 读取记忆中的历史上下文 + 当前输入，合并为完整 prompt
  │ Hook 拦截事件，实时翻译为 OpenAI SSE Chunk
  ▼
SseEmitter → LobeChat 打字机渲染
```

**核心思路：** 用 Hook 在 JVM 内部直接拦截 Agent 事件，翻译成 OpenAI 格式推送。不走 AGUI 中转，零额外网络跳转。

---

## 文件结构

```
org.example.common.openai
├── config/
│   ├── OpenAiAdapterProperties.java    配置类（API Key、group、超时）
│   └── OpenAiWebMvcConfig.java         拦截器注册 + CORS
├── controller/
│   └── OpenAiApiController.java        接口入口（/v1/models + /v1/chat/completions）
├── dispatcher/
│   └── StatelessAgentDispatcher.java   消息转换 + 记忆注入 + Agent 执行
├── hook/
│   └── OpenAiFormatHook.java           事件翻译 Hook（AgentScope → OpenAI SSE）
├── interceptor/
│   └── ApiKeyInterceptor.java          Bearer Token 校验
└── model/
    ├── OpenAiChatRequest.java          请求体模型（兼容多模态 content）
    └── OpenAiChunk.java                SSE 响应 Chunk 模型
```

---

## 配置

在 `application.yml` 中添加：

```yaml
openai:
  adapter:
    enabled: true
    # API Key 列表，LobeChat 等客户端必须携带其中之一
    api-keys:
      - sk-agentcar-2026-abc123
      - sk-agentcar-2026-def456
    # 暴露哪些 Agent：只有 group = "openai" 的 Agent 才会被 /v1/models 返回
    group: openai
    # SSE 长连接超时（毫秒），默认 30 分钟
    sse-timeout: 1800000
```

---

## Agent 要求

### 启用 Memory（推荐）

适配层会将前端传来的完整对话历史注入 Agent 记忆，Agent 能看到完整上下文：

```java
@AgentDefinition(
        name = "my-agent",
        description = "我的Agent",
        group = "openai",           // 必须属于 openai 分组
        scope = "prototype",        // 建议 prototype，避免并发串线
        enableMemory = true         // 启用记忆
)
public class MyAgent extends AbstractAgentTemplate {

    @Override
    protected String setupSysPrompt() {
        return "你是一个助手";
    }

    @Override
    protected Model setupCustomModel() {
        return components.model().dashScope().buildDashScopeModel();
    }

    @Override
    protected AutoContextMemory setupCustomMemory() {
        // 纯内存记忆，不落盘（enablePersistence 默认 false）
        return components.memory()
                .builder(setupCustomModel())
                .msgThreshold(100)     // 消息数阈值，超过触发压缩
                .maxToken(4000)        // 最大 token 数
                .tokenRatio(0.7)       // 压缩比例
                .lastKeep(3)           // 保留最近 N 轮不压缩
                .build();
    }
}
```

### 消息注入流程

```
前端 messages[8条]（system + 3轮 user/assistant + 最新 user）
  ↓
转换为 8 个 AgentScope Msg
  ↓
前 7 条 → agent.getMemory().addMessage(msg) 逐条注入记忆
第 8 条 → agent.call(lastMsg) 触发执行
  ↓
Agent 合并：记忆[7条] + 当前输入[1条] = 完整 8 条上下文 → LLM
```

### 不启用 Memory

如果不需要 Agent 看到历史上下文，也可以 `enableMemory = false`，适配层会把所有消息拼成一段文本直接传入 `call()`：

```
[system]
你是xxx

[user]
第一句话

[assistant]
第一句回复

[user]
第二句话
```

---

## 接口说明

### GET /v1/models

返回 `group = "openai"` 的 Agent 列表，LobeChat 配置界面会调这个接口拉取可选模型。

```json
{
  "object": "list",
  "data": [
    { "id": "my-agent", "object": "model", "owned_by": "agentscope" }
  ]
}
```

### POST /v1/chat/completions

核心对话接口，完全兼容 OpenAI 格式。支持流式和非流式两种模式。

**请求体：**

```json
{
  "model": "my-agent",
  "messages": [
    { "role": "system", "content": "你是一个助手" },
    { "role": "user", "content": "你好" },
    { "role": "assistant", "content": "你好！有什么可以帮你的？" },
    { "role": "user", "content": "今天天气怎么样" }
  ],
  "stream": true
}
```

> **多模态兼容：** `content` 字段同时支持 `String`（纯文本）和 `Array`（多模态，如 `[{type: "text", text: "..."}]`），适配层自动处理两种格式。

**流式响应（stream: true）：**

```
data: {"id":"chatcmpl-xxx","object":"chat.completion.chunk","choices":[{"delta":{"role":"assistant"}}]}

data: {"id":"chatcmpl-xxx","object":"chat.completion.chunk","choices":[{"delta":{"content":"你"}}]}
data: {"id":"chatcmpl-xxx","object":"chat.completion.chunk","choices":[{"delta":{"content":"好"}}]}

data: {"id":"chatcmpl-xxx","object":"chat.completion.chunk","choices":[{"delta":{"reasoning_content":"正在思考..."}}]}

data: {"id":"chatcmpl-xxx","object":"chat.completion.chunk","choices":[{"finish_reason":"stop"}]}
data: [DONE]
```

**非流式响应（stream: false）：**

```json
{
  "id": "chatcmpl-xxx",
  "object": "chat.completion",
  "model": "my-agent",
  "choices": [
    {
      "index": 0,
      "message": { "role": "assistant", "content": "你好！有什么可以帮你的？" },
      "finish_reason": "stop"
    }
  ],
  "usage": { "prompt_tokens": 0, "completion_tokens": 0, "total_tokens": 0 }
}
```

---

## 事件翻译规则

| AgentScope 事件 | OpenAI 字段 | 说明 |
|---|---|---|
| `ThinkingBlock` | `reasoning_content` | 深度思考内容，LobeChat 以折叠样式展示 |
| `TextBlock` | `content` | 正文，打字机效果 |
| `ToolUseBlock` | `content` | 工具调用提示（🔧 标记） |
| `ActingChunkEvent` | `content` | 工具执行中间进度（⚙️ 标记） |
| `PostActingEvent` | `content` | 工具执行结果（✅ 标记，超过 500 字符自动截断） |
| 执行结束 | `finish_reason: "stop"` + `[DONE]` | 流结束信号 |
| 执行异常 | `content`（❌ 标记）+ `stop` + `[DONE]` | 异常也会优雅关闭流 |

---

## CoPaw / QwenPaw 接入

通过 CoPaw 的自定义 Provider API 接入：

```bash
# 1. 创建自定义 provider
curl -X POST http://127.0.0.1:7903/api/models/custom-providers \
  -H "Content-Type: application/json" \
  -d '{"name": "agentscope", "provider_type": "openai", "display_name": "AgentScope"}'

# 2. 配置 base_url 和 api_key
curl -X PUT http://127.0.0.1:7903/api/models/{provider_id}/config \
  -H "Content-Type: application/json" \
  -d '{"base_url": "http://127.0.0.1:8089/v1", "api_key": "sk-agentcar-2026-abc123"}'

# 3. 添加模型（id 必须与 Agent 的 name 一致）
curl -X POST http://127.0.0.1:7903/api/models/{provider_id}/models \
  -H "Content-Type: application/json" \
  -d '{"id": "my-agent", "display_name": "我的Agent"}'

# 4. 设置为当前 agent 的活跃模型
curl -X PUT http://127.0.0.1:7903/api/models/active \
  -H "Content-Type: application/json" \
  -d '{"model_id": "agentscope/my-agent", "scope": "agent", "agent_id": "default"}'
```

> **注意：** `base_url` 只填到 `/v1`，不要填到 `/v1/chat/completions`，否则 SDK 会拼成双重路径。

---

## LobeChat 配置

在 LobeChat 的「设置 → 语言模型」中添加自定义服务商：

| 字段 | 填写内容 |
|---|---|
| API Endpoint | `http://你的IP:端口/v1` |
| API Key | yml 中配置的任意一个 Key，如 `sk-agentcar-2026-abc123` |
| 模型 | 下拉自动拉取，对应 Agent 的 name |

---

## 注意事项

1. **Agent 作用域**：必须使用 `prototype`，每次请求新建实例，避免并发串线
2. **记忆注入**：`agent.getMemory().addMessage(msg)` 是正确的方法，不要用 `getMessages().addAll()`
3. **SSE 格式**：使用 `SseEmitter.event().data(json)` 发送，不要手动拼 `data:` 前缀，否则会出现 `data:data:{json}` 双重前缀导致 SDK 解析失败
4. **content 字段**：请求体中的 `content` 必须用 `Object` 类型接收（可能是 String 或 Array），不能直接用 String
5. **Group 过滤**：只有 `@AgentDefinition(group = "openai")` 的 Agent 才暴露给外部，其他 Agent 不可见
6. **Memory vs 记忆**：`enableMemory = true` 配合 `enablePersistence = false`（默认）= 纯内存记忆，不写盘
7. **工具输出截断**：工具结果超过 500 字符会自动截断，防止 SSE 帧过大

---

## 踩坑记录

| 问题 | 原因 | 修复 |
|---|---|---|
| `No acceptable representation` | Spring `produces = TEXT_EVENT_STREAM_VALUE` 限制了响应格式 | 去掉 `produces` 限制 |
| `Unknown exception when connecting` | SSE 双重 `data:` 前缀：手动拼了 `data:` 后又调 `emitter.send()` 被 Spring 再包一层 | 用 `SseEmitter.event().data(json)` |
| 多轮对话丢失上下文 | `getMessages().addAll()` 不生效，返回的是副本 | 改用 `addMessage(msg)` 逐条注入 |
| `content` 反序列化失败 | CoPaw 发送数组格式 content，Java 侧用 String 接收 | `content` 改为 `Object` 类型 + `extractText()` |
| base_url 双重路径 | 填了 `/v1/chat/completions`，SDK 又拼一次 | 只填到 `/v1` |
