


# 开发者文档：MAS-FanPlus 技能构建系统

## 1. 概述

在 `MAS-FanPlus` 架构中，技能（Skill）是 Agent 执行任务的核心能力单元。为了提高代码的可读性与维护性，我们引入了基于 **Builder 模式** 的 `BaseSkillBoxFactory` 以及支持**链式响应**的 `Skill` 渲染引擎。该方案解决了复杂 Agent 技能组装时参数冗余、结构混乱的问题。

---

## 2. BaseSkillBoxFactory：技能盒构建器

`BaseSkillBoxFactory` 提供了一个流式 API，用于创建和配置 `io.agentscope.core.skill.SkillBox`。

### 2.1 核心组件

* **Factory**: 作为 Spring 管理的 `@Component`，负责启动构建流程。
* **SkillBoxBuilder**: 内部构建器，持有 `SkillBox` 实例并负责工具（Toolkit）与技能（AgentSkill）的注册。

### 2.2 API 参考

| 方法 | 说明 | 返回值 |
| --- | --- | --- |
| `create()` | 启动构建流程，初始化 `SkillBox` 及 `Toolkit`。 | `SkillBoxBuilder` |
| `create(Toolkit)` | 用指定 Toolkit 启动构建流程。 | `SkillBoxBuilder` |
| `addSkillWithTools(skill, tools...)` | 将 Skill 与工具绑定注册（支持 POJO/AgentTool/SubAgent/MCP）。 | `SkillBoxBuilder` |
| `addOnlySkill(skill)` | 注册纯 Skill（无 Tool）。 | `SkillBoxBuilder` |
| `addGitSkills(url)` | 加载远程 Git 仓库全部 Skill + 开启代码执行。 | `SkillBoxBuilder` |
| `addGitSkills(url, codeExec)` | 同上，可选是否开启代码执行。 | `SkillBoxBuilder` |
| `addGitSkills(url, codeExec, patterns...)` | **正则过滤**加载指定 Skill。 | `SkillBoxBuilder` |
| `enableSkillLoadTool()` | 启用 Skill 加载工具。 | `SkillBoxBuilder` |
| `buildSkillBox()` | 结束构建，返回 `SkillBox`。 | `SkillBox` |
| `createBaseAgentSkill(...)` | 静态方法：创建 `AgentSkill`。 | `AgentSkill` |
| `createBaseAgentSkillFromMd(md)` | 静态方法：从 Markdown 创建 `AgentSkill`。 | `AgentSkill` |

---

## 3. Skill：复合型 Prompt 渲染引擎

`Skill` 类（位于 `compositePrompt` 包下）专门用于构建结构化的、高质量的 Agent 指令。通过语义化的组件（Rule, Box, SubSection），确保大模型能够精准理解任务边界。

### 3.1 组装规范

一个标准的技能 Prompt 组装应遵循以下逻辑顺序：

1. **Identity (Role Definition)**: 定义 Agent 的职业背景与资历。
2. **Constraints (Rules)**: 明确任务中必须遵守的硬性规则。
3. **Knowledge/Context (Boxes)**: 提供参考资料或敏感词库。
4. **Output Specification**: 规定最终输出的格式（如 Markdown 表格或列表）。

---

## 4. Git Skill 远程加载

`BaseSkillBoxFactory` 集成了 `GitSkillManager`，支持从远程 Git 仓库自动加载 Skill。

### 4.1 addGitSkills 方法详解

```java
// 加载全部 Skill
.addGitSkills("https://github.com/your-org/skills.git")

// 不开启代码执行
.addGitSkills("https://github.com/your-org/skills.git", false)

// 正则过滤：只加载 data- 开头的
.addGitSkills("https://github.com/your-org/skills.git", true, "data-.*")

// 多个正则（任一匹配即加载）
.addGitSkills("https://github.com/your-org/skills.git", true, "data-.*", ".*security.*")
```

**正则匹配规则：** `String.matches(pattern)`

| pattern | 效果 |
| --- | --- |
| `"data-analysis"` | 精确匹配 |
| `"data-.*"` | 所有 data- 开头的 |
| `".*security.*"` | 名称含 security 的 |
| 不传 / null | 加载全部 |

### 4.2 完整使用示例

```java
@AgentDefinition(
    name = "DataAnalyst",
    skillRepoUrl = "https://github.com/your-org/skills.git",
    skillNames = {"data-.*"}
)
@Component
public class DataAnalystAgent extends AbstractAgentTemplate {

    public DataAnalystAgent(AgentComponentFacade components) {
        super(components);
    }

    @Override
    protected String setupSysPrompt() {
        return "你是一个数据分析专家。";
    }

    // 不覆盖 setupSkills() → 自动按注解配置加载 Git Skill
}
```

混合本地 + 远程：

```java
@Override
protected SkillBox setupSkills() {
    AgentSkill localSkill = BaseSkillBoxFactory.createBaseAgentSkill(
        "helper", "辅助", "提供帮助"
    );
    return components.skillBox().create(getToolkit())
            .addGitSkills(getSkillRepoUrl(), true, "data-.*")  // 远程 Git
            .addOnlySkill(localSkill)                            // 本地
            .buildSkillBox();
}
```

> 📖 完整文档见 `framework/git-skill-readme.md`

---

## 5. 实践示例：组装 Java 安全审计技能

以下示例展示了如何结合使用 `Skill` 渲染引擎与 `BaseSkillBoxFactory`。

### 5.1 定义技能 Prompt 内容

```java
String codeReviewSkillMd = Skill
                .create("Java_Security_Review", "专门用于检测 Java 代码中的安全漏洞和规范问题")
                .skillSubSection("Role Definition",
                        new ZeroShotPrompt()
                                .of("你是一个拥有10年经验的资深架构师，专注于代码安全审计。")
                                .rule("SQL注入", "必须检查所有数据库操作是否使用了预编译语句 (PreparedStatement)")
                                .rule("空指针", "检查所有可能为 null 的输入参数")
                                .box("敏感类名单", "Unsafe, Reflection, Runtime.exec").render()
                )
                .skillSubSection("Output Format",
                        "请以 Markdown 列表形式输出审计结果，包含：\n1. 漏洞等级\n2. 问题代码行号\n3. 修复建议")
                .render();
```

### 5.2 注册至 SkillBox

```java
public void setupAgent() {
    AgentSkill securitySkill = BaseSkillBoxFactory.createBaseAgentSkillFromMd(codeReviewSkillMd);

    SkillBox finalBox = BaseSkillBoxFactory.create()
            .registerSkillLoadTool(new Toolkit(), securitySkill)
            .build();
}
```
