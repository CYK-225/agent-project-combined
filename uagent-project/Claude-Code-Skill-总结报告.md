# Claude Code 技能总结报告

> 桌面版 / CLI / Web 版通用 | 2026-05-11

---

## 一、模型配置

| 操作 | 命令/方式 | 说明 |
|------|----------|------|
| 切换主模型 | `/model` | 会话级切换 |
| 快速模式 | `/fast` | Opus 4.6 快速输出，不降级模型 |
| 设置默认模型 | `settings.json` → `"model"` | 持久化配置 |
| 子代理指定模型 | Agent 工具 `model` 参数 | `opus` / `sonnet` / `haiku` 三选一 |

**模型家族：** Opus 4.7（最强）、Sonnet 4.6（平衡）、Haiku 4.5（最快最便宜）

---

## 二、CLAUDE.md 规则体系

| 层级 | 路径 | 作用域 |
|------|------|--------|
| 全局 | `~/.claude/CLAUDE.md` | 所有项目 |
| 项目 | `项目根/CLAUDE.md` | 当前项目 |
| 父目录 | `父目录/CLAUDE.md` | 子项目继承 |

**用途：** 定义编码规范、子代理模型分配策略、项目特定规则。

---

## 三、核心 Slash 命令

| 命令 | 功能 |
|------|------|
| `/model` | 切换模型 |
| `/fast` | 切换快速模式 |
| `/config` | 打开设置 |
| `/help` | 帮助信息 |
| `/clear` | 清空上下文 |
| `/review` | 审查 PR |
| `/simplify` | 审查代码质量并优化 |
| `/init` | 初始化 CLAUDE.md |
| `/loop` | 定时循环执行任务 |

---

## 四、子代理（Agent）架构

```
主代理（你选择的模型）
  ├── Explore    → 代码搜索、文件定位（快，只读）
  ├── Plan       → 架构设计、实现规划
  └── general    → 通用多步骤任务
```

- 子代理拥有**独立上下文**，不共享对话历史
- 通过 `model` 参数可为每个子代理指定不同模型
- 在 CLAUDE.md 中写入分配策略可持久化控制

---

## 五、Hooks（自动化钩子）

配置位置：`settings.json` → `"hooks"`

```json
{
  "hooks": {
    "PreToolUse": [{ "matcher": "Bash", "command": "echo before" }],
    "PostToolUse": [{ "matcher": "Write", "command": "echo after" }]
  }
}
```

支持事件：`PreToolUse`、`PostToolUse`、`Notification`、`Stop`

---

## 六、权限与安全

| 配置项 | 位置 | 说明 |
|--------|------|------|
| 允许列表 | `settings.json` → `"permissions"` | 免确认的工具/命令 |
| 项目级权限 | `.claude/settings.json` | 仅当前项目 |
| 全局权限 | `~/.claude/settings.json` | 所有项目 |

---

## 七、记忆系统

- **位置：** `~/.claude/projects/<项目>/memory/`
- **类型：** `user`（用户画像）、`feedback`（行为偏好）、`project`（项目上下文）、`reference`（外部资源）
- **索引：** `MEMORY.md` 汇总所有记忆条目

---

## 八、定时任务

| 方式 | 说明 |
|------|------|
| `CronCreate` | 会话内 cron 调度（不持久化） |
| `CronCreate(durable: true)` | 持久化到 `.claude/scheduled_tasks.json` |
| `/loop 5m` | 每 5 分钟循环执行 |
| `ScheduleWakeup` | 动态间隔唤醒 |

---

## 九、关键目录结构

```
~/.claude/
  ├── settings.json          # 全局设置
  ├── CLAUDE.md              # 全局规则
  ├── keybindings.json       # 快捷键
  ├── scheduled_tasks.json   # 持久化定时任务
  └── projects/<项目>/memory/ # 项目记忆

项目根/
  ├── CLAUDE.md              # 项目规则
  └── .claude/
      ├── settings.json      # 项目设置
      └── settings.local.json # 本地设置（不提交）
```

---

*报告生成时间：2026-05-11 | 适用于 Claude Code 最新版*
