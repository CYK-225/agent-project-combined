# AgentServiceHR - HR智能体服务

## 📁 目录结构

```
AgentServiceHR/src/main/java/org/example/agent/
├── config.java                    # AG-UI 配置类
├── HR/                            # HR智能体模块
│   ├── HRAgent.java              # HR智能体主类
│   ├── HrResumeScreenerAgent.java # 简历筛选智能体
│   ├── dal/                       # 数据访问层
│   │   ├── entity/
│   │   │   └── HrAgentTaskEntity.java  # 任务实体类
│   │   └── mapper/
│   │       └── HrAgentTaskMapper.java  # Mapper接口
│   ├── prompt/                    # 提示词
│   │   └── FristScorePrompt.java
│   └── tools/                     # 工具类（待实现）
└── utils/                         # 工具类
```

## 🏗️ 架构说明

### 数据流

```
工具平台
    │
    │ (1) 创建任务：任务id + 端口 + 回调地址
    ↓
AgentSport平台
    │
    │ (2) 创建HRAgent
    ↓
HRAgent
    │
    │ (3) 调工具 → 挂起(ToolSuspendException)
    ↑
    │ (5) 恢复执行
    │
    │ (6) 发送日志
    ↓
工具平台
    │
    │ (4) 调容器执行
    ↓
容器
```

### 核心组件

| 组件 | 说明 |
|------|------|
| `HRAgent` | HR智能体主类，继承AbstractAgentTemplate |
| `HrAgentTaskEntity` | 任务实体类，对应hr_agent_task表 |
| `HrAgentTaskMapper` | MyBatis Mapper接口 |
| `config.java` | AG-UI配置，注册Agent |

## 📊 数据库

### 表名：hr_agent_task

**数据源**：postgresql-session

**字段说明**：

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGSERIAL | 主键 |
| agent_name | VARCHAR(100) | Agent名称 |
| task_id | VARCHAR(100) | 任务ID（唯一） |
| session_id | VARCHAR(100) | 会话ID |
| container_port | INTEGER | 容器端口号 |
| instruction | TEXT | 指令内容 |
| log | TEXT | 日志 |
| model_output | TEXT | 模型输出 |
| callback_url | VARCHAR(500) | 回调地址 |
| status | VARCHAR(20) | 状态 |
| error_message | TEXT | 错误信息 |
| created_by | VARCHAR(100) | 创建者 |
| updated_by | VARCHAR(100) | 修改者 |
| created_at | TIMESTAMP | 创建时间 |
| updated_at | TIMESTAMP | 更新时间 |
| started_at | TIMESTAMP | 开始时间 |
| completed_at | TIMESTAMP | 完成时间 |

## 🔧 配置

### application-test.yml

```yaml
spring:
  datasource:
    dynamic:
      datasource:
        postgresql-session:
          url: jdbc:postgresql://8.163.67.126:5433/postgres?currentSchema=session
          username: postgres
          password: TideLink0131
```

### AG-UI 配置

在 `config.java` 中注册：

```java
@Bean
@AguiAgentId("hr-agent")
public AguiAgentRegistryCustomizer exposeHRAgentToAgui() {
    return registry -> {
        registry.registerFactory(
                "hr-agent",
                () -> agentPoolManager.getAgent("hr-agent")
        );
    };
}
```

## 🚀 待完成

- [ ] 实现工具类（使用挂起机制）
- [ ] 实现工具平台接口
- [ ] 实现日志回调功能

---

*更新时间：2026-05-27*
