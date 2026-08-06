# SkillOpt - 文本梯度下降优化 Agent 技能

基于论文 [SkillOpt (arXiv:2605.23899)](https://arxiv.org/abs/2605.23899) 的实现。

## 核心思路

固定模型，只优化技能文本。通过迭代循环让技能越来越好：

```
初始技能 → 评估 → 分析失败 → 有界修改 → 再评估 → ... → 优化后技能
```

## 输入

| 输入 | 说明 | 必须？ |
|------|------|--------|
| `skill.md` | 初始技能文本 | 🟡 可选（可自动生成） |
| `tasks.json` | 任务集（含期望结果） | ✅ 必须 |
| `verifier.py` | 验证器（判断成功/失败） | ✅ 必须 |

## 产出

| 产出 | 说明 |
|------|------|
| `skill_optimized.md` | 优化后的技能文本 |
| `rejected_edits.json` | 被拒绝的编辑记录 |
| `optimization_log.json` | 每轮迭代的评估结果 |

## 项目结构

```
SkillOpt/
├── README.md              # 本文件
├── skill.md               # 初始技能（待填）
├── skill_optimized.md     # 优化后技能（产出）
├── tasks.json             # 任务集（待填）
├── verifier.py            # 验证器（待填）
├── optimizer.py           # 核心优化循环
├── llm_client.py          # LLM 调用封装
├── rejected_edits.json    # 被拒绝编辑记忆
└── optimization_log.json  # 优化日志
```

## 运行

```bash
cd SkillOpt
python optimizer.py --epochs 10 --learning-rate 0.3
```

## 状态

- [ ] 等待确定任务场景和技能
- [ ] 实现核心优化循环
- [ ] 实现验证器
- [ ] 测试运行
