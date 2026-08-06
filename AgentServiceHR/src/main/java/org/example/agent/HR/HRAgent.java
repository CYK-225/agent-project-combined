package org.example.agent.HR;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.hook.Hook;
import io.agentscope.core.model.ExecutionConfig;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.StructuredOutputReminder;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.tool.ToolkitConfig;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.example.acl.hook.callRQHook;
import org.example.agentScope.mas.phone.tool.HrTools;
import org.example.agentScope.framework.annotation.AgentDefinition;
import org.example.agentScope.framework.core.AbstractAgentTemplate;
import org.example.agentScope.framework.core.AgentComponentFacade;
import org.example.agentScope.mas.phone.hook.ScreenshotInjectionHook;
import org.example.agentScope.util.hooksManager.HookBuilder;
import org.example.agentScope.util.hooksManager.SessionContext;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * HR智能体 - 人力资源助手
 * <p>
 * 负责HR相关的任务处理，包括简历筛选、面试安排等。
 * 使用工具挂起机制与容器通信。
 * </p>
 * 
 * <h3>架构说明：</h3>
 * <pre>
 * 工具平台 → 创建任务 → HRAgent → 调工具 → 挂起 → 工具平台调容器 → 恢复执行
 * </pre>
 */
@EqualsAndHashCode(callSuper = true)
@Slf4j
@Component
@AgentDefinition(
    name = "hr-agent",
    description = "HR智能体，负责人力资源相关任务处理",
    group = "hr",
    enableMemory = true,
    enablePlan = false,
    maxIters = 50
)
public class HRAgent extends AbstractAgentTemplate {
    private final SessionContext sessionContext = new SessionContext();
    /** 工具注入：使用 ObjectProvider 支持 prototype 作用域 */
//    private final ObjectProvider<HrTools> hrToolsFactory;

    protected HRAgent(AgentComponentFacade components, ObjectProvider<HrTools> hrToolsFactory) {
        super(components);
//        this.hrToolsFactory = hrToolsFactory;
        log.info("[HRAgent] 构造函数被调用");
    }

    /**
     * 系统提示词 - 定义HR Agent的行为
     */
    private static final String SYS_PROMPT = """
            # HR智能体 - GUI自动化助手

            ## 角色
            你是一个专业的HR助手，具备**桌面GUI自动化操作能力**。你可以直接操作电脑桌面，完成各种HR相关任务。

            ## 核心能力
            1. **GUI桌面操作**：你可以操控鼠标和键盘，打开应用、点击按钮、输入文字、滚动页面等
            2. **简历筛选**：在招聘网站上搜索和筛选候选人简历
            3. **面试安排**：在日历系统中安排面试、发送通知
            4. **员工管理**：在HR系统中处理入离职、考勤等事务
            5. **数据分析**：在Excel或其他系统中生成HR报表

            ## 可用的GUI工具
            你可以使用以下工具直接操作桌面：
            - **gui_open_app(app_name)** - 打开应用程序（如 "chrome", "notepad", "excel"）
            - **gui_left_click(x, y)** - 左键点击屏幕坐标
            - **gui_right_click(x, y)** - 右键点击
            - **gui_double_click(x, y)** - 双击
            - **gui_triple_click(x, y)** - 三击（选中整行）
            - **gui_middle_click(x, y)** - 鼠标中键点击
            - **gui_type(text)** - 输入文字（支持中文，通过剪贴板粘贴）
            - **gui_key(keys)** - 按键（如 "enter", "ctrl+a", "ctrl,c"）
            - **gui_scroll(pixels)** - 滚动页面（负数向下，正数向上）
            - **gui_mouse_move(x, y)** - 移动鼠标
            - **gui_drag(x, y)** - 从当前位置拖拽到指定坐标
            - **gui_wait(seconds)** - 等待指定秒数（等待页面加载）
            - **gui_reset()** - 按 Win+D 回到桌面，清场重来

            ## 工作流程
            1. 理解用户的任务需求
            2. **立即调用GUI工具执行操作**，不要只回复文字
            3. 使用 gui_open_app 打开需要的应用
            4. 使用 gui_left_click 点击目标位置
            5. 使用 gui_type 输入需要的文字
            6. 操作完成后汇报结果

            ## 重要规则
            - **收到任务后必须调用工具执行，不要只用文字回复！**
            - 如果用户说"打开浏览器"，直接调用 gui_open_app("chrome")
            - 如果用户说"点击某个按钮"，直接调用 gui_left_click(x, y)
            - 先操作，再汇报结果
            - 遇到不确定的坐标，可以先 gui_mouse_move 试探位置
            """;

    /**
     * 配置系统提示词
     */
    @Override
    protected String setupSysPrompt() {
        log.info("[HRAgent] 设置系统提示词");
        return SYS_PROMPT;
    }

    /**
     * 配置模型
     * <p>
     * 使用DashScope的qwen-plus模型
     * </p>
     */
    @Override
    protected Model setupCustomModel() {
        log.info("[HRAgent] 配置模型");
        return components.model().dashScope().buildDashScopeModel("视觉模型");
    }

    /**
     * 配置工具集
     * <p>
     * 注册HrTools，使用工具挂起机制。
     * 通过 ObjectProvider 获取 prototype 作用域的 HrTools 实例，
     * 确保每个 Agent 拥有独立的工具实例。
     * </p>
     */
    @Override
    protected Toolkit setupTools() {
        log.info("[HRAgent] 注册工具集");

        return components.toolkit()
                .create(ToolkitConfig.builder()
                        .parallel(false) // HR Agent 不需要并行工具执行
                        .allowToolDeletion(false) // 不允许删除工具，保持工具集稳定
                        .executionConfig(ExecutionConfig.builder()
                                .timeout(Duration.ofSeconds(300)) // 工具调用超时设置为5分钟，考虑到GUI操作可能较慢
                                .build())
                        .build())
                .addTools(new HrTools())
                .build();
    }


    /**
     * Agent构建完成后的回调
     * 
     * @param agent 构建完成的ReActAgent实例
     */
    @Override
    public void afterAgentBuilt(ReActAgent agent) {
        log.info("[HRAgent] HR Agent初始化完成，agent: {}", agent);
    }

    @Override
    public StructuredOutputReminder setupStructuredOutputReminder(){
        return StructuredOutputReminder.TOOL_CHOICE;
    }
}
