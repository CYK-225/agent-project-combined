package org.example.agentScope.mas.msgHub.Definition;


public class DebateResult {
    public boolean finished;
    public String correctAnswer;
    public String reasoning;
    public int roundsUsed;

    // 静态工厂：快速创建一个“未解决”的结果
    public static DebateResult unresolved() {
        DebateResult r = new DebateResult();
        r.finished = false;
        r.correctAnswer = "无结论 (达到最大轮次)";
        return r;
    }
}
