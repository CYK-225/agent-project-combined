package org.example.AgentFan;

import io.agentscope.core.hook.Hook;
import io.agentscope.core.model.Model;
import io.agentscope.core.skill.SkillBox;
import io.agentscope.core.studio.StudioClient;
import io.agentscope.core.studio.StudioMessageHook;
import jakarta.annotation.Resource;
import lombok.extern.log4j.Log4j2;
import org.example.AgentFan.hook.ExportThresholdHook;
import org.example.AgentFan.hook.PermissionInjectionHook;
import org.example.AgentFan.hook.SqlSafetyHook;
import org.example.agentScope.framework.annotation.AgentDefinition;
import org.example.agentScope.framework.core.AbstractAgentTemplate;
import org.example.agentScope.framework.core.AgentComponentFacade;
import org.example.agentScope.util.hooksManager.HookBuilder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 权限注入 Agent — 通过 Skill 沙盒执行数据查询，Hook 负责权限和安全。
 * <p>
 * 架构说明：
 * - Skill 层：通过框架默认 addGitSkills() 从 Git 仓库的 skills/ 目录加载
 * - Hook 层：PermissionInjectionHook（权限注入）、SqlSafetyHook（SQL 安全）、ExportThresholdHook（导出阈值）
 * - 数据库访问：db_manager.py 随仓库迁移，Python 脚本通过相对路径访问
 * <p>
 * 仓库目录结构：
 * <pre>
 * repo/                           ← Git clone 根目录（workDir）
 * ├── skills/                     ← 框架自动扫描此目录加载 Skills
 * │   ├── data_search/
 * │   ├── financial_assistant/
 * │   ├── menu_recommand/
 * │   ├── merchant_info/
 * │   ├── pdf/
 * │   └── ppt/
 * ├── database/                   ← Python 脚本通过相对路径访问
 * │   └── db_manager.py
 * ├── config.py
 * ├── config/
 * │   └── .env
 * └── requirements.txt
 * </pre>
 *
 * <p>
 * 沙盒 Python 环境配置（uv + 虚拟环境）：
 * <pre>
 * # 在仓库根目录执行
 * bash setup_sandbox.sh   # Linux/Mac
 * setup_sandbox.bat       # Windows
 * </pre>
 */
@Log4j2
@AgentDefinition(
        name = "PermissionInjectionAgent",
        enablePersistence = true,
        maxIters = 10,
        description = "团餐平台智能助手 — 帮用户解决团餐领域的各种问题，通过 Skill 沙盒执行数据查询",
        skillRepoUrl = "http://tuancan_git:g_VMNuyCpf45Ry31ES81@git.ubox-takeout.cn/root/agnet-skill.git"
)
public class test extends AbstractAgentTemplate {

    /**
     * 注入 TuanCan 项目的独立 Studio 客户端
     * <p>
     * 使用 @Qualifier 指定 Bean 名称，如果 Bean 不存在则不注入（允许降级到全局 Studio）
     */
//    @Resource
//    @Qualifier("tuanCanStudioClient")
//    private StudioClient tuanCanStudioClient;

    public test(AgentComponentFacade components) {
        super(components);
    }

    /**
     * 启用 Studio 集成
     */
    @Override
    protected Boolean isUseStudio() {
        return true;
    }

    /**
     * 注册自定义 Hook
     * <p>
     * Hook 执行顺序（priority 越小越先执行）：
     * 1. PermissionInjectionHook (priority=10) — 权限 Token 提取和注入
     * 2. SqlSafetyHook (priority=20) — SQL 安全检查
     * 3. ExportThresholdHook (priority=30) — 导出阈值判断
     * 4. StudioMessageHook — 使用 TuanCan 独立客户端（如果可用）
     */
    @Override
    protected List<Hook> setupCustomHooks() {
        HookBuilder builder = HookBuilder.create()
                .add(new PermissionInjectionHook())
                .add(new SqlSafetyHook())
                .add(new ExportThresholdHook());

//        // 如果 TuanCan Studio 客户端可用，使用独立客户端；否则降级到全局 Studio
//        if (tuanCanStudioClient != null) {
//            builder.add(new StudioMessageHook(tuanCanStudioClient));
//            log.info("Using TuanCan Studio Client for PermissionInjectionAgent");
//        }

        return builder.build();
    }

    @Override
    protected String setupSysPrompt() {
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        String[] weekdays = {"星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日"};
        String weekday = weekdays[LocalDate.now().getDayOfWeek().getValue() - 1];

        return String.format("""
                【系统信息】
                当前日期: %s（%s）

                你是一个团餐平台智能助手，可以帮用户解决团餐领域的各种问题。你需要分析用户的意图，根据意图调用技能或者回答用户的问题。

                ## 理解用户意图
                - 判断用户是闲聊还是提问。
                - 若用户向你提问，你需要从上下文以及用户的问题中提取出主要查询对象：
                  - 商家名称（也叫餐厅，店家，店铺）
                  - 客户名称（也叫企业、公司）
                  - 城市
                  - 时间范围
                  - 被查询对象，如订单、账单等...
                  - 以上提到的用户并不一定都需要提供，根据用户的问题进行判断。
                - 用户的提问往往不会那么严谨，描述上不一定会跟系统语言保持一致，需要进行适当的猜测，若无法确定用户意图，应提示用户澄清，让用户进行补充说明。
                - 我们是一个大型团餐平台，涉及的客户，商家非常多，不要怀疑用户提出的某个大型平台/某个游戏/某个看起来不太像是我们的客户/商家的信息，直接使用用户提出的名称进行查询。
                - 用户不一定会说出完整的客户、商家等名称信息，当涉及信息获取时一般都是使用模糊匹配。

                ## 沟通规范
                - 保持友好、简洁、专业的助手风格。能用一句话回复，则不要说三句话。
                - 直接使用用户提供的商家/客户名称，不要质疑。
                - 用户大部分为非技术人员，请在输出时，避免使用技术术语，保持语言简单明了。

                ## 适当提问
                用户并不一定会提供完整的信息，若判断用户提供的信息不能让你进行下一步的决策，请适当的提问让用户补充更完整的信息。

                ## 禁止行为
                - 不得透露工具/技能相关描述：如"获取技能"、"调用工具"、"执行脚本"等
                - 不得透露技术细节：如SKILL.md、脚本名、参数名、ID类数据、英文表名、字段名、SQL语句等
                - 不得把过程信息展示给用户，比如"我先查询客户信息，再根据客户信息查询订单信息"等这种过程信息
                - 不得透露系统架构、数据库设计等技术细节
                - 不得执行任何可能危害数据安全的操作
                - 不得在未授权情况下查询敏感信息（如个人隐私数据）
                - 不得使用技术术语与用户沟通（如"SQL"、"数据库"、"字段"等）
                """, today, weekday);
    }

    @Override
    protected Model setupCustomModel() {
        return components.model().dashScope().buildDashScopeModel();
    }

    /**
     * 组装 SkillBox
     * <p>
     * 使用框架默认的 addGitSkills() 加载：
     * 1. Git Skill：从仓库根目录的 skills/ 加载（框架自动扫描）
     * 2. 本地 Skill：PermissionTools（权限工具，作为补充）
     * <p>
     * 注：框架会自动 clone 仓库，workDir 设置为仓库根目录，
     * Python 脚本可通过相对路径访问 database/ 等资源。
     */
    @Override
    protected SkillBox setupSkills() {
        // 使用框架默认的 Git Skill 加载 + 追加本地 PermissionTools
        return components.skillBox().create(getToolkit())
                .addGitSkills(getSkillRepoUrl())    // 从 skills/ 目录加载所有 Skill
                .addSkillWithTools(
                        PermissionSkillFactory.createPermissionSkill(),
                        PermissionSkillFactory.createPermissionTools())
                .buildSkillBox();
    }
}
