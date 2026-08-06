//package org.example.masfanplus.Common.proptcraft;
//
//
//
//import io.agentscope.core.skill.SkillBox;
//import io.agentscope.core.tool.Toolkit;
//import org.example.masfanplus.Common.proptcraft.compositePrompt.Skill;
//import org.example.masfanplus.Common.proptcraft.compositePrompt.ZeroShotPrompt;
//import org.example.masfanplus.AgentScope.util.skill.BaseSkillBoxFactory; // 假设这是你之前的工厂类
//import io.agentscope.core.skill.AgentSkill;
//
//
//public class SkillUsageDemo {
//
//    public static void main(String[] args) {
//        // ==========================================
//        // 场景：构建一个 "Java代码安全审查" 的技能 Prompt
//        // ==========================================
//
//        // 1. 使用静态工厂 create 入口（强制填写 name 和 description）
//        String codeReviewSkill = Skill
//                .create("Java_Security_Review", "专门用于检测 Java 代码中的安全漏洞和规范问题")
//                // 添加技能的核心角色设定
//                .skillSubSection("Role Definition",
//                        "你是一个拥有10年经验的资深架构师，专注于代码安全审计。")
//
//                // 添加具体的检查规则 (使用 rule 组件)
//                .rule("SQL注入", "必须检查所有数据库操作是否使用了预编译语句 (PreparedStatement)")
//                .rule("空指针", "检查所有可能为 null 的输入参数")
//
//                // 添加参考数据或上下文 (使用 box 组件)
//                .box("敏感类名单", "Unsafe, Reflection, Runtime.exec")
//
//                // 添加输出格式要求 (使用 skillSubSection 或 outputFormat 组件)
//                .skillSubSection("Output Format",
//                        "请以 Markdown 列表形式输出审计结果，包含：\n1. 漏洞等级\n2. 问题代码行号\n3. 修复建议")
//                .render()
//
//                ;
//        String codeReviewSkillMd = Skill
//                .create("Java_Security_Review", "专门用于检测 Java 代码中的安全漏洞和规范问题")
//                .skillSubSection("Role Definition",
//                        new ZeroShotPrompt()
//                                .of("你是一个拥有10年经验的资深架构师，专注于代码安全审计。")
//                                // 2. 注入规则
//                                .rule("SQL注入", "必须检查所有数据库操作是否使用了预编译语句 (PreparedStatement)")
//                                .rule("空指针", "检查所有可能为 null 的输入参数")
//                                // 3. 提供上下文数据
//                                .box("敏感类名单", "Unsafe, Reflection, Runtime.exec").render()
//                )
//
//                // 4. 定义输出规范
//                .skillSubSection("Output Format",
//                        "请以 Markdown 列表形式输出审计结果，包含：\n1. 漏洞等级\n2. 问题代码行号\n3. 修复建议")
//                .render(); // 渲染为最终的 Markdown 文本
//
//        // ==========================================
//        // 打印结果预览
//        // ==========================================
//        System.out.println("====== 生成的 Prompt 预览 ======");
//        System.out.println(codeReviewSkill);
//
//        // ==========================================
//        // 结合 AgentScope 的使用示例 (配合之前的 Factory)
//        // ==========================================
//
//        // 将生成的 Skill 对象转为 AgentScope 需要的实体
//        AgentSkill agentSkill = createBaseAgentSkillFromMd(codeReviewSkill);
//
//        System.out.println("\n====== AgentSkill 对象创建成功 ======");
//        System.out.println("Name: " + agentSkill.getName());
//
//        AgentSkill securitySkill = createBaseAgentSkillFromMd(codeReviewSkillMd);
//        SkillBox  box=BaseSkillBoxFactory.create()
//                .registerSkillLoadTool(new Toolkit() , securitySkill).build();
//
//        SkillBox skillBox = BaseSkillBoxFactory.create()
//                .registerSkillLoadTool(new Toolkit(), createBaseAgentSkillFromMd(codeReviewSkillMd))
//                .build();
//
//
//    }
//}
