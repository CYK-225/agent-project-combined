"""
LLM 调用封装
支持多种 LLM API（Qwen、DeepSeek、OpenAI 等）
"""
import json
import requests


class LLMClient:
    """统一的 LLM 调用接口"""
    
    def __init__(self, provider: str, api_key: str, base_url: str = None, model: str = None):
        """
        Args:
            provider: 'qwen', 'deepseek', 'openai'
            api_key: API Key
            base_url: 自定义 API 地址（可选）
            model: 模型名称（可选，有默认值）
        """
        self.provider = provider
        self.api_key = api_key
        self.base_url = base_url
        self.model = model or self._default_model()
    
    def _default_model(self) -> str:
        defaults = {
            'qwen': 'qwen-plus',
            'deepseek': 'deepseek-chat',
            'openai': 'gpt-4o',
        }
        return defaults.get(self.provider, 'gpt-4o')
    
    def _get_url(self) -> str:
        if self.base_url:
            return self.base_url
        urls = {
            'qwen': 'https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions',
            'deepseek': 'https://api.deepseek.com/v1/chat/completions',
            'openai': 'https://api.openai.com/v1/chat/completions',
        }
        return urls.get(self.provider)
    
    def chat(self, system_prompt: str, user_prompt: str, temperature: float = 0.7) -> str:
        """
        调用 LLM 进行对话
        
        Args:
            system_prompt: 系统提示词
            user_prompt: 用户消息
            temperature: 温度参数
            
        Returns:
            LLM 回复文本
        """
        url = self._get_url()
        headers = {
            'Content-Type': 'application/json',
            'Authorization': f'Bearer {self.api_key}',
        }
        payload = {
            'model': self.model,
            'messages': [
                {'role': 'system', 'content': system_prompt},
                {'role': 'user', 'content': user_prompt},
            ],
            'temperature': temperature,
        }
        
        try:
            resp = requests.post(url, headers=headers, json=payload, timeout=120)
            resp.raise_for_status()
            data = resp.json()
            return data['choices'][0]['message']['content']
        except Exception as e:
            print(f"[LLM ERROR] {e}")
            return None
    
    def analyze_evidence(self, current_skill: str, evidence: dict, rejected_edits: list) -> str:
        """
        分析执行证据，生成"文本梯度"（修改建议）
        
        Args:
            current_skill: 当前技能文本
            evidence: 执行证据 {successes: [...], failures: [...]}
            rejected_edits: 被拒绝的编辑记录
            
        Returns:
            修改建议文本
        """
        system_prompt = """你是一个 Agent 技能优化专家。
你的任务是分析 Agent 使用当前技能执行任务的结果，找出技能的不足，提出修改建议。

规则：
1. 只关注技能文本本身的问题，不要讨论模型能力
2. 修改建议要具体、可操作
3. 每次建议修改不超过 30% 的内容（有界学习率）
4. 不要重复之前被拒绝的修改"""
        
        user_prompt = f"""## 当前技能
```
{current_skill}
```

## 执行结果

### 成功的任务
{json.dumps(evidence.get('successes', []), ensure_ascii=False, indent=2)}

### 失败的任务
{json.dumps(evidence.get('failures', []), ensure_ascii=False, indent=2)}

## 之前被拒绝的修改（请避免重复）
{json.dumps(rejected_edits, ensure_ascii=False, indent=2) if rejected_edits else "无"}

---

请分析以上结果，提出具体的修改建议。输出格式：

## 分析
- 成功的原因：
- 失败的原因：

## 修改建议
（具体的、可操作的修改建议，不超过 30% 的内容）"""
        
        return self.chat(system_prompt, user_prompt, temperature=0.5)
    
    def apply_edit(self, current_skill: str, gradient: str, max_change_ratio: float = 0.3) -> str:
        """
        根据修改建议，生成新的技能文本
        
        Args:
            current_skill: 当前技能文本
            gradient: 修改建议
            max_change_ratio: 最大修改比例
            
        Returns:
            新的技能文本
        """
        system_prompt = """你是一个技能文本编辑器。
你的任务是根据修改建议，更新技能文本。

规则：
1. 保留原有技能中有效的部分
2. 只修改建议中提到的部分
3. 修改幅度不超过原文的 30%
4. 输出完整的更新后技能文本（不要省略）"""
        
        user_prompt = f"""## 当前技能
```
{current_skill}
```

## 修改建议
{gradient}

---

请根据修改建议，输出更新后的完整技能文本。只输出技能文本内容，不要加其他说明。"""
        
        return self.chat(system_prompt, user_prompt, temperature=0.3)
