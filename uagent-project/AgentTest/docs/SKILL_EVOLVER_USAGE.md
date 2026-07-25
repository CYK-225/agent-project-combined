# SkillEvolver 使用指南

## 快速开始

### 1. 一键 Demo（最简单）

```bash
# 浏览器直接打开或 curl，无需构造 JSON
curl http://localhost:8080/api/evolver/demo-submit
```

返回 taskId，异步触发 SkillEvolverLoop 图。

---

### 2. 同步测试（调试用，阻塞等结果）

```bash
curl -X POST http://localhost:8080/api/evolver/test-sync \
  -H "Content-Type: application/json" \
  -d '{
    "taskName": "test-echo",
    "instruction": "编写一个 Python 函数 echo(text)，返回反转后的字符串",
    "maxIterations": 1,
    "nExploration": 2
  }'
```

> `test-sync` 会阻塞到图执行完毕再返回，包含完整 graphState，适合开发调试。

---

### 3. 正式提交（异步）

```bash
curl -X POST http://localhost:8080/api/evolver/submit \
  -H "Content-Type: application/json" \
  -d '{
    "taskName": "sales-pivot-analysis",
    "instruction": "给定 sales.csv，编写 SQL 生成按月度+地区的 pivot 表，输出到 result.csv",
    "taskData": "{\"inputFile\": \"/data/sales.csv\"}",
    "verifier": "{\"fileExists\": [\"result.csv\"], \"containsColumn\": [\"month\", \"region\"]}",
    "rewardMode": "discrete",
    "maxIterations": 2,
    "nExploration": 4,
    "nValidation": 5
  }'
```

返回：
```json
{
  "taskId": "abc123def456",
  "threadId": "evolver-abc123def456",
  "status": "PENDING",
  "message": "进化任务已创建，SkillEvolverLoop 图已触发"
}
```

---

### 4. 查询结果

```bash
# 任务状态 + 最佳 Skill
curl http://localhost:8080/api/evolver/task/abc123def456

# Skill 版本链（看每轮迭代的 SKILL.md 演进）
curl http://localhost:8080/api/evolver/task/abc123def456/versions

# 最近任务列表
curl http://localhost:8080/api/evolver/tasks?limit=10
```

---

### 5. 诊断

```bash
curl http://localhost:8080/api/evolver/debug
```

---

## SubmitRequest 字段说明

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| `taskName` | String | 否 | - | 人类可读的任务名 |
| `instruction` | String | **是** | - | Agent 要完成的目标描述 |
| `taskData` | String(JSON) | 否 | - | 输入数据（文件路径、内联数据等） |
| `verifier` | String(JSON) | 否 | - | 验证规则（exit code、文件存在性、关键词匹配等） |
| `rewardMode` | String | 否 | `discrete` | `discrete`(pass/fail) / `continuous`(标量奖励) |
| `maxIterations` | Integer | 否 | `2` | Explore→Analyze→Update 循环轮数 R |
| `nExploration` | Integer | 否 | `4` | 每轮并行 trial 数 K |
| `nValidation` | Integer | 否 | `5` | 最终验证 trial 数 |

---

## 日常使用流程

```
开发一个新 Skill
    │
    ▼
POST /api/evolver/submit   ← 提交任务指令 + 验证规则
    │
    ▼
GET  /api/evolver/task/{id}  ← 轮询状态直到 COMPLETED
    │
    ▼
GET  /api/evolver/task/{id}/versions  ← 查看版本演进
    │
    ▼
取 bestSkillContent → 部署到生产环境的 SkillBox
```

---

## API 端点一览

| 方法 | 路径 | 说明 |
|---|---|---|
| `GET` | `/api/evolver/demo-submit` | 一键示例任务 |
| `POST` | `/api/evolver/submit` | 提交进化任务（异步） |
| `POST` | `/api/evolver/test-sync` | 同步测试（阻塞等结果） |
| `GET` | `/api/evolver/task/{taskId}` | 查询任务状态和结果 |
| `GET` | `/api/evolver/task/{taskId}/versions` | 查询 Skill 版本链 |
| `GET` | `/api/evolver/tasks?limit=20` | 列出所有任务 |
| `GET` | `/api/evolver/debug` | 诊断信息 |
