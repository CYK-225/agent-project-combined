"""
SkillOpt 核心优化循环
基于论文: SkillOpt - Optimizing Agent Skills via Textual Gradient Descent

输入: 初始技能 + 任务集 + 验证器
产出: 优化后的技能
"""
import json
import os
import sys
import argparse
from datetime import datetime

# 导入 LLM 客户端
from llm_client import LLMClient


class SkillOptimizer:
    """技能优化器 - 实现文本梯度下降"""
    
    def __init__(
        self,
        llm: LLMClient,
        initial_skill: str,
        tasks: list[dict],
        verifier_func,
        max_epochs: int = 10,
        learning_rate: float = 0.3,
    ):
        """
        Args:
            llm: LLM 客户端
            initial_skill: 初始技能文本
            tasks: 任务列表 [{"task": "...", "expected": ...}, ...]
            verifier_func: 验证函数 (task, outcome) -> bool/float
            max_epochs: 最大迭代轮数
            learning_rate: 学习率（控制每次修改幅度）
        """
        self.llm = llm
        self.skill = initial_skill
        self.tasks = tasks
        self.verifier = verifier_func
        self.max_epochs = max_epochs
        self.learning_rate = learning_rate
        
        # 被拒绝编辑记忆
        self.rejected_edits: list[str] = []
        
        # 优化日志
        self.log: list[dict] = []
    
    def evaluate(self, skill: str) -> dict:
        """
        用当前技能评估所有任务
        
        Returns:
            {
                "accuracy": float,          # 成功率
                "score": float,             # 平均分数
                "successes": [...],         # 成功的任务
                "failures": [...],          # 失败的任务
            }
        """
        successes = []
        failures = []
        
        for i, task in enumerate(self.tasks):
            task_desc = task["task"]
            expected = task.get("expected")
            
            # 用技能执行任务
            outcome = self._execute_task(skill, task_desc)
            
            # 验证结果
            result = self.verifier(task_desc, outcome, expected)
            
            entry = {
                "task": task_desc,
                "outcome": outcome,
                "result": result,
            }
            
            if result.get("passed", False):
                successes.append(entry)
            else:
                failures.append(entry)
            
            print(f"  [{i+1}/{len(self.tasks)}] {'✓' if result.get('passed') else '✗'} {task_desc[:50]}...")
        
        total = len(self.tasks)
        passed = len(successes)
        accuracy = passed / total if total > 0 else 0
        scores = [s["result"].get("score", 0) for s in successes + failures]
        avg_score = sum(scores) / len(scores) if scores else 0
        
        return {
            "accuracy": accuracy,
            "score": avg_score,
            "passed": passed,
            "total": total,
            "successes": successes,
            "failures": failures,
        }
    
    def _execute_task(self, skill: str, task: str) -> str:
        """
        用技能执行任务（让 LLM 模拟 Agent 行为）
        
        Args:
            skill: 当前技能文本
            task: 任务描述
            
        Returns:
            执行结果文本
        """
        system_prompt = f"""你是一个执行任务的 Agent。
请严格按照以下技能指导来完成任务。

## 技能指导
{skill}"""
        
        user_prompt = f"""请执行以下任务：

{task}

请直接输出执行结果，不要解释过程。"""
        
        return self.llm.chat(system_prompt, user_prompt, temperature=0.3) or ""
    
    def optimize(self) -> str:
        """
        执行优化循环
        
        Returns:
            优化后的技能文本
        """
        print("=" * 60)
        print("SkillOpt 优化开始")
        print(f"最大轮数: {self.max_epochs}")
        print(f"学习率: {self.learning_rate}")
        print(f"任务数: {len(self.tasks)}")
        print("=" * 60)
        
        best_skill = self.skill
        best_accuracy = 0
        
        for epoch in range(self.max_epochs):
            print(f"\n{'='*60}")
            print(f"Epoch {epoch + 1}/{self.max_epochs}")
            print(f"{'='*60}")
            
            # ── Step 1: 评估当前技能 ──
            print("\n[Step 1] 评估当前技能...")
            results = self.evaluate(self.skill)
            print(f"\n  结果: {results['passed']}/{results['total']} 通过, 准确率: {results['accuracy']:.1%}")
            
            # 记录日志
            epoch_log = {
                "epoch": epoch + 1,
                "accuracy": results["accuracy"],
                "score": results["score"],
                "passed": results["passed"],
                "total": results["total"],
            }
            
            # 更新最佳
            if results["accuracy"] > best_accuracy:
                best_accuracy = results["accuracy"]
                best_skill = self.skill
                print(f"  ★ 新的最佳准确率: {best_accuracy:.1%}")
            
            # 收敛检查
            if results["accuracy"] >= 1.0:
                print("\n🎉 所有任务都通过了！提前结束。")
                break
            
            # ── Step 2: 分析（文本梯度）──
            print("\n[Step 2] 分析执行结果，生成修改建议...")
            evidence = {
                "successes": [{"task": s["task"][:100], "outcome": s["outcome"][:200]} 
                             for s in results["successes"][:5]],  # 最多 5 个样本
                "failures": [{"task": f["task"][:100], "outcome": f["outcome"][:200], "reason": f["result"].get("reason", "")}
                            for f in results["failures"][:5]],
            }
            
            gradient = self.llm.analyze_evidence(
                current_skill=self.skill,
                evidence=evidence,
                rejected_edits=self.rejected_edits[-5:],  # 只保留最近 5 条
            )
            
            if not gradient:
                print("  ⚠ LLM 分析失败，跳过本轮")
                continue
            
            print(f"  修改建议生成完成 ({len(gradient)} 字符)")
            epoch_log["gradient_preview"] = gradient[:200]
            
            # ── Step 3: 更新（有界修改）──
            print("\n[Step 3] 应用修改，生成新技能...")
            new_skill = self.llm.apply_edit(
                current_skill=self.skill,
                gradient=gradient,
                max_change_ratio=self.learning_rate,
            )
            
            if not new_skill:
                print("  ⚠ LLM 编辑失败，跳过本轮")
                continue
            
            # 验证新技能是否更好
            print("\n  验证新技能...")
            new_results = self.evaluate(new_skill)
            
            if new_results["accuracy"] > results["accuracy"]:
                print(f"  ✓ 新技能更好: {results['accuracy']:.1%} → {new_results['accuracy']:.1%}")
                self.skill = new_skill
                self.rejected_edits = []  # 成功了就清空
                epoch_log["accepted"] = True
            else:
                print(f"  ✗ 新技能没有改善: {results['accuracy']:.1%} → {new_results['accuracy']:.1%}")
                self.rejected_edits.append(gradient)  # 记录被拒绝的编辑
                epoch_log["accepted"] = False
            
            self.log.append(epoch_log)
        
        print(f"\n{'='*60}")
        print(f"优化完成！最佳准确率: {best_accuracy:.1%}")
        print(f"{'='*60}")
        
        return best_skill
    
    def save(self, output_dir: str = "."):
        """保存优化结果"""
        # 保存优化后的技能
        skill_path = os.path.join(output_dir, "skill_optimized.md")
        with open(skill_path, 'w', encoding='utf-8') as f:
            f.write(self.skill)
        print(f"\n✓ 优化后技能已保存: {skill_path}")
        
        # 保存被拒绝编辑
        rejected_path = os.path.join(output_dir, "rejected_edits.json")
        with open(rejected_path, 'w', encoding='utf-8') as f:
            json.dump(self.rejected_edits, f, ensure_ascii=False, indent=2)
        print(f"✓ 被拒绝编辑已保存: {rejected_path}")
        
        # 保存优化日志
        log_path = os.path.join(output_dir, "optimization_log.json")
        with open(log_path, 'w', encoding='utf-8') as f:
            json.dump(self.log, f, ensure_ascii=False, indent=2)
        print(f"✓ 优化日志已保存: {log_path}")


def default_verifier(task: str, outcome: str, expected=None) -> dict:
    """
    默认验证器（用 LLM 评判）
    如果你有自己的验证逻辑，请替换这个函数
    
    Returns:
        {"passed": bool, "score": float, "reason": str}
    """
    # 这里只是一个简单的 LLM 评判示例
    # 实际使用时请根据你的任务替换
    return {"passed": True, "score": 1.0, "reason": "placeholder"}


def main():
    parser = argparse.ArgumentParser(description="SkillOpt - 技能优化器")
    parser.add_argument('--provider', default='deepseek', choices=['qwen', 'deepseek', 'openai'])
    parser.add_argument('--api-key', required=True, help='LLM API Key')
    parser.add_argument('--base-url', default=None, help='自定义 API 地址')
    parser.add_argument('--model', default=None, help='模型名称')
    parser.add_argument('--skill', default='skill.md', help='初始技能文件')
    parser.add_argument('--tasks', default='tasks.json', help='任务集文件')
    parser.add_argument('--epochs', type=int, default=10, help='最大迭代轮数')
    parser.add_argument('--learning-rate', type=float, default=0.3, help='学习率')
    parser.add_argument('--output-dir', default='.', help='输出目录')
    
    args = parser.parse_args()
    
    # 初始化 LLM
    llm = LLMClient(
        provider=args.provider,
        api_key=args.api_key,
        base_url=args.base_url,
        model=args.model,
    )
    
    # 加载技能
    with open(args.skill, 'r', encoding='utf-8') as f:
        skill = f.read()
    
    # 加载任务
    with open(args.tasks, 'r', encoding='utf-8') as f:
        tasks = json.load(f)
    
    # 创建优化器
    optimizer = SkillOptimizer(
        llm=llm,
        initial_skill=skill,
        tasks=tasks,
        verifier_func=default_verifier,
        max_epochs=args.epochs,
        learning_rate=args.learning_rate,
    )
    
    # 运行优化
    optimized_skill = optimizer.optimize()
    
    # 保存结果
    optimizer.save(args.output_dir)


if __name__ == '__main__':
    main()
