
---

# ModelFactory 模块技术文档

## 1. 模块概述

`ModelFactory` 是 `MASFanPlus` 项目中用于构建和管理大语言模型（LLM）实例的核心工厂模块。该模块封装了 **DashScope (阿里云灵积)** 和 **Ollama (本地化部署)** 两种模型服务的构建逻辑，支持通过静态工厂方法快速创建符合 `AgentScope` 标准的 `Model` 对象。

**核心特性：**

* **多源支持**：统一封装了云端（Qwen-Plus）与本地（Ollama）模型的调用接口。
* **场景化预设**：通过策略模式（OptionName）针对“思考”、“工具”、“聊天”等不同任务场景预置了最优参数（如 Temperature、Context Window）。
* **流式与思维链**：DashScope 模型默认开启流式输出 (`stream`) 与思维链 (`enableThinking`)。

---

## 2. 环境配置

在使用本模块前，需在 Spring Boot 配置文件（`application.yml` 或 `application.properties`）中配置以下参数。工厂类通过 `@Value` 注解注入这些配置。

```yaml
# application.yml 配置示例

qwen:
  apiKey: "sk-..."          # [必填] 阿里云 DashScope API Key

ollama:
  base-url: "http://localhost:11434" # [必填] Ollama 服务地址
  modelName: "llama3"                # [可选] 默认调用的本地模型名称

```

---

以下是修正后的 **API 方法参考** 部分，修复了表格格式错乱的问题，并优化了描述的可读性。

---

### 3.1 DashScopeModelBuilder

**类路径**: `org.example.masfanplus.ModelFactory.DashScopeModelBuilder`

| 方法 | 返回类型 | 描述 |
| --- | --- | --- |
| `buildDashScopeModel()` | `Model` | **默认构建**。使用配置的 API Key 构建默认模型（`qwen-plus`），默认开启思维链（Thinking）和流式输出（Stream），温度设为 0.7。 |
| `buildDashScopeModel(String modelName, Double temperature)` | `Model` | **自定义构建**。支持指定模型名称与温度。若参数为 `null`，则自动回退到默认值（模型名默认为 `qwen-plus`，温度默认为 0.7）。 |
| `buildDashScopeModel(GenerateOptions options)` | `Model` | **高级构建**。使用完全自定义的 `GenerateOptions` 配置对象构建模型，适用于需要精细控制 TopP、MaxTokens 等参数的场景。 |
| `buildDashScopeModel(String OptionName)` | `Model` | **场景化构建**。根据预设的场景名称（"思考"、"工具"、"聊天"）自动应用经过调优的参数配置。 |

### 3.2 OllamaModelBuilder

**类路径**: `org.example.masfanplus.ModelFactory.OllamaModelBuilder`

| 方法 | 返回类型 | 描述 |
| --- | --- | --- |
| `buildOllamaModel()` | `Model` | **默认构建**。构建默认本地模型，默认参数配置为：`numCtx=8192`, `temperature=0.0`, `topK=40`, `topP=0.9`, `repeatPenalty=1.1`。 |
| `buildOllamaModel(String modelName, Double temperature)` | `Model` | **自定义构建**。覆盖默认的模型名称和温度。若参数为 `null`，自动使用配置文件中的 `modelName` 和默认温度 `0.7`。 |
| `buildOllamaModel(OllamaOptions options)` | `Model` | **高级构建**。传入完整的 `OllamaOptions` 对象，支持自定义所有底层参数（如 `numCtx`, `topK`, `repeatPenalty` 等）。 |
| `buildOllamaModel(String OptionName)` | `Model` | **场景化构建**。根据预设场景自动调整参数。特别注意在 "工具" 模式下，上下文窗口会自动缩减为 4096 以提升指令遵循能力。 |
---

## 4. 场景化预设 (OptionName) 详解

工厂方法支持传入 `String OptionName` 快速切换参数策略。以下是各模式的内部参数对照：

| 场景模式 (OptionName) | DashScope 参数策略 | Ollama 参数策略 | 适用场景 |
| --- | --- | --- | --- |
| **"思考"** | `temp=0.5` | `temp=0.5`, `ctx=8192` | 适用于复杂逻辑推理、规划任务，降低随机性。 |
| **"工具"** | `temp=0.1` | `temp=0.1`, **`ctx=4096`** | 适用于函数调用（Function Calling）、JSON 生成，极低的温度确保格式稳定。 |
| **"聊天"** | `temp=0.7` | `temp=0.8`, `ctx=8192` | 适用于开放域对话、创意写作，较高的温度提升回答的多样性。 |

---

## 5. 代码使用示例

```java
import io.agentscope.core.model.Model;
import org.example.masfanplus.agentScope.util.modelFactory.DashScopeModelBuilder;
import org.example.masfanplus.agentScope.util.modelFactory.OllamaModelBuilder;
import org.springframework.stereotype.Service;

@Service
public class ModelService {

    public void initModels() {
        // 1. 创建一个用于严谨逻辑推理的 Qwen 模型
        Model reasoningModel = DashScopeModelBuilder.buildDashScopeModel("思考");

        // 2. 创建一个用于工具调用的本地 Ollama 模型 (低温度，高稳定性)
        Model toolModel = OllamaModelBuilder.buildOllamaModel("工具");

        // 3. 自定义创建一个高温度的创意模型
        Model creativeModel = DashScopeModelBuilder.buildDashScopeModel("qwen-max", 1.2);

        // 后续将 model 注入 Agent...
    }
}

```

## 6. 注意事项

1. **静态字段注入**: `DashScopeModelBuilder` 和 `OllamaModelBuilder` 使用了 `@Value` 注解注入 `static` 字段。请确保这些类被 Spring 容器扫描并初始化，否则静态字段可能为 `null`。
2. **默认 API Key**: `DashScopeModelBuilder` 代码中包含一个硬编码的备用 Key (`sk-e26...`)，生产环境建议检查并确保通过配置文件覆盖此值。
3. **Ollama 上下文限制**: 在 "工具" 模式下，Ollama 模型的上下文窗口被显式限制为 `4096`，如需处理超长文本请避免使用此预设或使用高级构建方法。