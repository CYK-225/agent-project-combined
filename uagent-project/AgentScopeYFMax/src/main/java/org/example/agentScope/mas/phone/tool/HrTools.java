package org.example.agentScope.mas.phone.tool;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.tool.ToolSuspendException;
import lombok.extern.slf4j.Slf4j;
import org.example.acl.apiClient.GuiApiClient;
import org.example.repository.dal.entity.DataModel;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * HR工具集 - GUI自动化工具
 * <p>
 * 对应容器的13个GUI操作方法，使用工具挂起机制。
 * 工具调用 GuiApiClient 异步发送请求到容器，然后抛出 ToolSuspendException，
 * 等待容器回调返回结果后恢复Agent执行。
 * </p>
 *
 * <h3>容器地址注入机制（ToolExecutionContext + DataModel）：</h3>
 * <pre>
 * AgentTaskExecutorService 在创建 Agent 时，将 DataModel（包含容器URL）注册到
 * ToolExecutionContext 中。当 Agent 调用工具时，框架自动将 DataModel 注入到
 * 工具方法的参数中（无需 @ToolParam 注解）。
 *
 * 调用链路：
 * AgentTaskExecutorService.invokeAgent()
 *   → new DataModel(containerUrl)
 *   → ToolExecutionContext.builder().register(dataModel).build()
 *   → getAgentWithSession(agentName, sessionId, context, null)
 *   → agent.call(msg)                // Agent 执行，LLM 调工具
 *     → HrTools.guiLeftClick(x, y, model)  // 框架自动注入 DataModel
 *     → new GuiApiClient(model.getUrl())   // 构造客户端
 *     → client.leftClick(step, x, y)       // 异步发送到容器
 *     → throw ToolSuspendException         // 挂起等待容器回调
 * </pre>
 *
 * <h3>设计决策：</h3>
 * <ul>
 *   <li>LLM 不需要知道容器地址，DataModel 由框架自动注入</li>
 *   <li>每个工具方法最后一个参数是 DataModel（无 @ToolParam），LLM 不会看到它</li>
 *   <li>step 计数器自动递增，LLM 不需要传 step</li>
 * </ul>
 *
 * @author AgentScope-Team
 * @version 4.0 - 使用 ToolExecutionContext + DataModel 注入，替代 ThreadLocal/ConcurrentHashMap
 */
@Slf4j
@Component
@Scope("prototype")
public class HrTools {

    // ==================== 步骤计数器 ====================

    /** 步骤计数器（每个任务独立） */
    private final AtomicInteger stepCounter = new AtomicInteger(0);

    /** 重置步骤计数器（新任务开始时调用） */
    public void resetStepCounter() {
        stepCounter.set(0);
        log.info("[HrTools] 步骤计数器已重置");
    }

    /** 获取下一个步骤编号 */
    private int getNextStep() {
        return stepCounter.incrementAndGet();
    }

    // ==================== 鼠标操作工具 ====================

    /**
     * 左键单击
     *
     * @param x X坐标（0-999）
     * @param y Y坐标（0-999）
     * @param model 容器上下文（框架自动注入，LLM 不传）
     */
    @Tool(name = "gui_left_click", description = "在屏幕指定位置执行鼠标左键单击")
    public ToolResultBlock guiLeftClick(
            @ToolParam(name = "x", description = "X坐标（0-999）", required = true) int x,
            @ToolParam(name = "y", description = "Y坐标（0-999）", required = true) int y,
            DataModel model) throws ToolSuspendException {

        int step = getNextStep();
        GuiApiClient client = new GuiApiClient(model.getUrl());
        log.info("[HrTools] gui_left_click，step: {}, x: {}, y: {}", step, x, y);

        client.leftClick(step, x, y);
        throw new ToolSuspendException("左键单击于 [" + x + ", " + y + "]，等待容器执行结果");
    }

    /**
     * 右键单击
     *
     * @param x X坐标（0-999）
     * @param y Y坐标（0-999）
     * @param model 容器上下文（框架自动注入）
     */
    @Tool(name = "gui_right_click", description = "在屏幕指定位置执行鼠标右键单击")
    public ToolResultBlock guiRightClick(
            @ToolParam(name = "x", description = "X坐标（0-999）", required = true) int x,
            @ToolParam(name = "y", description = "Y坐标（0-999）", required = true) int y,
            DataModel model) throws ToolSuspendException {

        int step = getNextStep();
        GuiApiClient client = new GuiApiClient(model.getUrl());
        log.info("[HrTools] gui_right_click，step: {}, x: {}, y: {}", step, x, y);

        client.rightClick(step, x, y);
        throw new ToolSuspendException("右键单击于 [" + x + ", " + y + "]，等待容器执行结果");
    }

    /**
     * 双击
     *
     * @param x X坐标（0-999）
     * @param y Y坐标（0-999）
     * @param model 容器上下文（框架自动注入）
     */
    @Tool(name = "gui_double_click", description = "在屏幕指定位置执行鼠标双击")
    public ToolResultBlock guiDoubleClick(
            @ToolParam(name = "x", description = "X坐标（0-999）", required = true) int x,
            @ToolParam(name = "y", description = "Y坐标（0-999）", required = true) int y,
            DataModel model) throws ToolSuspendException {

        int step = getNextStep();
        GuiApiClient client = new GuiApiClient(model.getUrl());
        log.info("[HrTools] gui_double_click，step: {}, x: {}, y: {}", step, x, y);

        client.doubleClick(step, x, y);
        throw new ToolSuspendException("双击于 [" + x + ", " + y + "]，等待容器执行结果");
    }

    /**
     * 三击（选中整行文字）
     *
     * @param x X坐标（0-999）
     * @param y Y坐标（0-999）
     * @param model 容器上下文（框架自动注入）
     */
    @Tool(name = "gui_triple_click", description = "在屏幕指定位置执行鼠标三击，选中整行文字")
    public ToolResultBlock guiTripleClick(
            @ToolParam(name = "x", description = "X坐标（0-999）", required = true) int x,
            @ToolParam(name = "y", description = "Y坐标（0-999）", required = true) int y,
            DataModel model) throws ToolSuspendException {

        int step = getNextStep();
        GuiApiClient client = new GuiApiClient(model.getUrl());
        log.info("[HrTools] gui_triple_click，step: {}, x: {}, y: {}", step, x, y);

        client.tripleClick(step, x, y);
        throw new ToolSuspendException("三击于 [" + x + ", " + y + "]，等待容器执行结果");
    }

    /**
     * 中键单击
     *
     * @param x X坐标（0-999）
     * @param y Y坐标（0-999）
     * @param model 容器上下文（框架自动注入）
     */
    @Tool(name = "gui_middle_click", description = "在屏幕指定位置执行鼠标中键单击")
    public ToolResultBlock guiMiddleClick(
            @ToolParam(name = "x", description = "X坐标（0-999）", required = true) int x,
            @ToolParam(name = "y", description = "Y坐标（0-999）", required = true) int y,
            DataModel model) throws ToolSuspendException {

        int step = getNextStep();
        GuiApiClient client = new GuiApiClient(model.getUrl());
        log.info("[HrTools] gui_middle_click，step: {}, x: {}, y: {}", step, x, y);

        client.middleClick(step, x, y);
        throw new ToolSuspendException("中键单击于 [" + x + ", " + y + "]，等待容器执行结果");
    }

    /**
     * 鼠标移动
     *
     * @param x X坐标（0-999）
     * @param y Y坐标（0-999）
     * @param model 容器上下文（框架自动注入）
     */
    @Tool(name = "gui_mouse_move", description = "将鼠标移动到指定位置")
    public ToolResultBlock guiMouseMove(
            @ToolParam(name = "x", description = "X坐标（0-999）", required = true) int x,
            @ToolParam(name = "y", description = "Y坐标（0-999）", required = true) int y,
            DataModel model) throws ToolSuspendException {

        int step = getNextStep();
        GuiApiClient client = new GuiApiClient(model.getUrl());
        log.info("[HrTools] gui_mouse_move，step: {}, x: {}, y: {}", step, x, y);

        client.mouseMove(step, x, y);
        throw new ToolSuspendException("鼠标移动到 [" + x + ", " + y + "]，等待容器执行结果");
    }

    /**
     * 拖动
     *
     * @param x 目标X坐标（0-999）
     * @param y 目标Y坐标（0-999）
     * @param model 容器上下文（框架自动注入）
     */
    @Tool(name = "gui_drag", description = "从当前位置拖动到指定位置")
    public ToolResultBlock guiDrag(
            @ToolParam(name = "x", description = "目标X坐标（0-999）", required = true) int x,
            @ToolParam(name = "y", description = "目标Y坐标（0-999）", required = true) int y,
            DataModel model) throws ToolSuspendException {

        int step = getNextStep();
        GuiApiClient client = new GuiApiClient(model.getUrl());
        log.info("[HrTools] gui_drag，step: {}, x: {}, y: {}", step, x, y);

        client.drag(step, x, y);
        throw new ToolSuspendException("拖动到 [" + x + ", " + y + "]，等待容器执行结果");
    }

    // ==================== 键盘操作工具 ====================

    /**
     * 输入文字（通过剪贴板粘贴，支持中文）
     *
     * @param text 要输入的文字内容
     * @param model 容器上下文（框架自动注入）
     */
    @Tool(name = "gui_type", description = "通过剪贴板粘贴方式输入文字，支持中文")
    public ToolResultBlock guiType(
            @ToolParam(name = "text", description = "要输入的文字内容", required = true) String text,
            DataModel model) throws ToolSuspendException {

        int step = getNextStep();
        GuiApiClient client = new GuiApiClient(model.getUrl());
        log.info("[HrTools] gui_type，step: {}, text: {}", step, text);

        client.type(step, text);
        throw new ToolSuspendException("输入文字 '" + text + "'，等待容器执行结果");
    }

    /**
     * 按键（支持组合键）
     * <p>
     * 常用按键：enter, backspace, delete, tab, escape, up, down, left, right
     * 组合键示例：["ctrl", "a"] 全选, ["ctrl", "c"] 复制, ["ctrl", "v"] 粘贴
     * </p>
     *
     * @param keys 按键名称，多个用逗号分隔（如 "ctrl,a" 表示 Ctrl+A）
     * @param model 容器上下文（框架自动注入）
     */
    @Tool(name = "gui_key", description = "按下键盘按键，支持组合键（如 ctrl,a 表示全选）")
    public ToolResultBlock guiKey(
            @ToolParam(name = "keys", description = "按键名称，多个用逗号分隔（如：enter, ctrl+a, ctrl,c）", required = true) String keys,
            DataModel model) throws ToolSuspendException {

        int step = getNextStep();
        GuiApiClient client = new GuiApiClient(model.getUrl());
        log.info("[HrTools] gui_key，step: {}, keys: {}", step, keys);

        // 支持多种分隔方式：逗号、加号、空格
        List<String> keyList = Arrays.stream(keys.split("[,\\+\\s]+"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();

        client.key(step, keyList);
        throw new ToolSuspendException("按键 " + keyList + "，等待容器执行结果");
    }

    /**
     * 滚动
     * <p>
     * 负数向下滚动，正数向上滚动。建议值：-300（向下）, 300（向上）
     * </p>
     *
     * @param pixels 滚动像素值
     * @param model 容器上下文（框架自动注入）
     */
    @Tool(name = "gui_scroll", description = "垂直滚动鼠标滚轮，负数向下，正数向上")
    public ToolResultBlock guiScroll(
            @ToolParam(name = "pixels", description = "滚动像素值，负数向下，正数向上（如 -300）", required = true) int pixels,
            DataModel model) throws ToolSuspendException {

        int step = getNextStep();
        GuiApiClient client = new GuiApiClient(model.getUrl());
        log.info("[HrTools] gui_scroll，step: {}, pixels: {}", step, pixels);

        client.scroll(step, pixels);
        throw new ToolSuspendException("滚动 " + pixels + " 像素，等待容器执行结果");
    }

    // ==================== 系统操作工具 ====================

    /**
     * 打开应用
     * <p>
     * 常用应用名：google-chrome（Chrome浏览器）, chromium, firefox, nautilus（文件管理器）
     * </p>
     *
     * @param appName 应用程序名称
     * @param model 容器上下文（框架自动注入）
     */
    @Tool(name = "gui_open_app", description = "启动应用程序（如 google-chrome）")
    public ToolResultBlock guiOpenApp(
            @ToolParam(name = "app_name", description = "应用名称（如 google-chrome, chromium, firefox）", required = true) String appName,
            DataModel model) throws ToolSuspendException {

        int step = getNextStep();
        GuiApiClient client = new GuiApiClient(model.getUrl());
        log.info("[HrTools] gui_open_app，step: {}, appName: {}", step, appName);

        client.openApp(step, appName);
        throw new ToolSuspendException("打开应用 '" + appName + "'，等待容器执行结果");
    }

    /**
     * 等待指定秒数
     *
     * @param seconds 等待秒数（建议2-5秒）
     * @param model 容器上下文（框架自动注入）
     */
    @Tool(name = "gui_wait", description = "等待指定秒数，用于等待页面加载")
    public ToolResultBlock guiWait(
            @ToolParam(name = "seconds", description = "等待秒数（建议2-5秒）", required = true) int seconds,
            DataModel model) throws ToolSuspendException {

        int step = getNextStep();
        GuiApiClient client = new GuiApiClient(model.getUrl());
        log.info("[HrTools] gui_wait，step: {}, seconds: {}", step, seconds);

        client.wait(step, seconds);
        throw new ToolSuspendException(STR."等待 \{seconds} 秒，等待容器执行结果");
    }

    /**
     * 重置桌面（Win+D）
     *
     * @param model 容器上下文（框架自动注入）
     */
    @Tool(name = "gui_reset", description = "按 Win+D 回到桌面，清场重来")
    public ToolResultBlock guiReset(DataModel model) throws ToolSuspendException {

        int step = getNextStep();
        GuiApiClient client = new GuiApiClient(model.getUrl());
        log.info("[HrTools] gui_reset，step: {}", step);

        client.reset(step);
        throw new ToolSuspendException("重置桌面，等待容器执行结果");
    }
}
