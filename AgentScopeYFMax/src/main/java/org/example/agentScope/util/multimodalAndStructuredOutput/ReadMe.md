

---

# AgentScope 多模态与结构化输出指南

本文档详细介绍了 AgentScope 的多模态处理能力与结构化数据生成机制，涵盖核心架构、使用流程、配置规范及 API 参考。

## 1. 多模态 (Multimodal)

多模态功能赋予 Agent 理解和生成图像、音频、视频等多种媒体内容的能力。AgentScope 采用统一的架构设计，确保不同模型间的兼容性与灵活性。

### 1.1 核心架构：ContentBlock

AgentScope 使用 `ContentBlock` 体系作为所有内容的基类，实现了文本与媒体内容的混合处理与自动模型适配。

* **架构统一**：`ContentBlock` 统领所有内容类型。
* **混合消息**：单条 `Msg` 可包含 Text、Image、Audio 等多种 Block。
* **灵活来源**：支持 Base64（推荐，兼容性高）和 URL/本地路径两种加载方式。

**ContentBlock 继承体系：**

* `TextBlock`: 文本内容
* `ImageBlock`: 图像内容
* `AudioBlock`: 音频内容
* `VideoBlock`: 视频内容
* `ThinkingBlock`: 推理过程
* `ToolUseBlock` / `ToolResultBlock`: 工具调用与结果

### 1.2 快速集成指南

#### 步骤 1：构建媒体内容块

支持通过 `Base64Source` 或 `URLSource` 构建媒体块。

**支持的 MIME 类型：**

* **图像**: `image/png`, `image/jpeg`, `image/gif`, `image/webp`
* **音频**: `audio/mp3`, `audio/wav`, `audio/mpeg`
* **视频**: `video/mp4`, `video/mpeg`

```java
import io.agentscope.core.message.*;
import java.util.Base64;
import java.nio.file.Files;
import java.nio.file.Paths;

// 1. 图像：Base64 方式（推荐）
String base64Image = Base64.getEncoder().encodeToString(Files.readAllBytes(Paths.get("image.png")));
ImageBlock imageBlock = ImageBlock.builder()
    .source(Base64Source.builder()
        .data(base64Image)
        .mediaType("image/png")
        .build())
    .build();

// 2. 图像：URL 方式
ImageBlock urlImage = ImageBlock.builder()
    .source(URLSource.builder()
        .url("https://example.com/image.jpg")
        .build())
    .build();

// 3. 视频示例
VideoBlock videoBlock = VideoBlock.builder()
    .source(URLSource.builder()
        .url("https://example.com/video.mp4")
        .build())
    .build();

```

#### 步骤 2：构建多模态消息

将多个 Block 组合进 `content` 列表中。

```java
Msg multiModalMsg = Msg.builder()
    .role(MsgRole.USER)
    .content(List.of(
        TextBlock.builder().text("请分析这张图片的内容：").build(),
        imageBlock // 传入上面创建的 ImageBlock
    ))
    .build();

```

#### 步骤 3：配置 Vision Agent

**注意**：使用 DashScope 视觉模型（如 `qwen-vl-max`）时，**必须**配置 `DashScopeChatFormatter`。

```java
import io.agentscope.core.ReActAgent;
import io.agentscope.core.formatter.dashscope.DashScopeChatFormatter;
import io.agentscope.core.model.DashScopeChatModel;

ReActAgent agent = ReActAgent.builder()
    .name("VisionAssistant")
    .sysPrompt("你是一个具有视觉能力的 AI 助手。")
    .model(DashScopeChatModel.builder()
        .apiKey(System.getenv("DASHSCOPE_API_KEY"))
        .modelName("qwen-vl-max") // 支持 qwen-vl-plus, qwen-audio-turbo 等
        .stream(true)
        .formatter(new DashScopeChatFormatter()) // 关键配置
        .build())
    .build();

// 执行调用
Msg response = agent.call(multiModalMsg).block();
System.out.println(response.getTextContent());

```

---

## 2. 结构化输出 (Structured Output)

结构化输出允许 Agent 将非结构化的自然语言转换为符合预定义 Schema 的强类型数据（Java Object），便于业务系统集成。

### 2.1 核心模式

AgentScope 支持两种实现模式，可根据模型能力通过 `structuredOutputReminder` 进行配置。

| 模式 | 配置项 | 特点 | 适用场景 |
| --- | --- | --- | --- |
| **TOOL_CHOICE** | `StructuredOutputReminder.TOOL_CHOICE` | **默认模式**，利用模型原生的工具调用能力，通常一次 API 调用即可完成。 | 支持 Function Call 的模型 (如 gpt-4, qwen3-max) |
| **PROMPT** | `StructuredOutputReminder.PROMPT` | 通过提示词引导模型输出 JSON，可能涉及多次交互与解析。 | 不支持 Function Call 的老旧模型 |

### 2.2 开发流程

#### 1. 定义 Schema (POJO)

定义一个标准的 Java 类，**必须包含无参构造函数**。支持嵌套对象、集合类型及 Jackson 注解。

```java
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class ProductInfo {
    @JsonProperty("product_name") // 自定义 JSON 字段名
    public String name;
    
    public Double price;
    public List<String> features;
    public Address origin; // 支持嵌套对象
    
    public ProductInfo() {} // 必需
}

public class Address {
    public String city;
    public String country;
}

```

#### 2. 请求与处理

在 `call` 方法中传入目标类 Class 对象，并从响应中提取数据。

```java
// 1. 发送请求，指定期望的返回类型 ProductInfo.class
Msg response = agent.call(userMsg, ProductInfo.class).block();

try {
    // 2. 提取结构化数据
    ProductInfo data = response.getStructuredData(ProductInfo.class);
    
    // 3. 业务逻辑处理
    System.out.println("产品: " + data.name);
    System.out.println("特性: " + data.features);
    
} catch (Exception e) {
    System.err.println("数据解析或验证失败: " + e.getMessage());
}

```

---

## 3. 方法参考 (API Reference)

以下是多模态与结构化输出相关的核心 API 方法说明。

| 方法/构造器 | 返回类型 | 描述 |
| --- | --- | --- |
| **Multimodal Builder** |  |  |
| `ImageBlock.builder()` | `ImageBlock.Builder` | 创建图像内容块构建器 |
| `AudioBlock.builder()` | `AudioBlock.Builder` | 创建音频内容块构建器 |
| `VideoBlock.builder()` | `VideoBlock.Builder` | 创建视频内容块构建器 |
| `Base64Source.builder()` | `Base64Source.Builder` | 使用 Base64 字符串数据创建媒体源（推荐） |
| `URLSource.builder()` | `URLSource.Builder` | 使用 URL 或本地路径创建媒体源 |
| **Agent Execution** |  |  |
| `agent.call(msg)` | `Mono<Msg>` | 标准消息调用，支持多模态输入，返回文本响应 |
| `agent.call(msg, outputClass)` | `Mono<Msg>` | **结构化调用**，请求 Agent 返回符合 `outputClass` Schema 的数据 |
| **Response Handling** |  |  |
| `msg.getTextContent()` | `String` | 获取消息中的纯文本内容 |
| `msg.getStructuredData(clazz)` | `T` | 从消息中反序列化并提取指定类型 `T` 的结构化对象 |
| **Configuration** |  |  |
| `DashScopeChatFormatter()` | `Formatter` | 阿里云 Qwen-VL 系列模型**必须**使用的格式化器 |
| `StructuredOutputReminder` | `Enum` | 结构化输出模式配置 (`TOOL_CHOICE` 或 `PROMPT`) |