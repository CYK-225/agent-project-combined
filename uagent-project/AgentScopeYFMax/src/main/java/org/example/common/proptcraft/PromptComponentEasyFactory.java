package org.example.common.proptcraft;/**
 * @Auter zzh
 * @Date 2025/10/10
 */

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import org.apache.commons.lang3.StringUtils;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * @projectName: AItest
 * @package: org.example.aitest.infrastructure.proptcraft.component
 * @className: PromptComponentUtil
 * @author: Eric
 * @description: TODO
 * @date: 2025/10/10 23:45
 * @version: 1.0
 */
public interface PromptComponentEasyFactory {

    /**
     * 【核心改动 1】定义一个契约：
     * 任何实现这个接口的类，都必须能“接收”一个组件，并返回它自己（以支持链式调用）。
     */
    PromptComponent append(PromptComponent component);

    String render();

    // --- 下面是改造后的默认方法，全部改为 return append(...) ---

    default PromptComponent system(String system) {
        return append(new PromptComponent() {
            @Override public String render() { return ": " + system; }
        });
    }

    default PromptComponent role(String role) {
        return append(new PromptComponent() {
            @Override public String render() { return ": " + role; }
        });
    }

    default PromptComponent user(String input) {
        return append(new PromptComponent() {
            @Override public String render() { return "USER: " + input; }
        });
    }

    default PromptComponent fewShot(String examples) {
        return append(new PromptComponent() {
            @Override public String render() { return "EXAMPLES:\n" + examples; }
        });
    }

    default PromptComponent safety() {
        return append(new PromptComponent() {
            @Override public String render() { return "SAFETY: 不要生成有害内容。"; }
        });
    }

    default PromptComponent tone(String tone) {
        return append(new PromptComponent() {
            @Override public String render() { return "TONE: " + tone; }
        });
    }

    default PromptComponent outputFormat(String format) {
        return append(new PromptComponent() {
            @Override public String render() { return "FORMAT: " + format; }
        });
    }

    default PromptComponent of(String content) {
        if (StringUtils.isBlank(content)) {
            return append(null);
        }
        return append(new PromptComponent() {
            @Override public String render() { return content; }
        });
    }

    default PromptComponent rule(String rule, String content) {

        return append(new PromptComponent() {
            @Override public String render() { return STR."\{rule}:\{content}"; }
        });
    }

    default PromptComponent box(String boxName, String boxContent) {
        if(StringUtils.isBlank(boxContent)) {
            return append(null);
        }
        return append(new PromptComponent() {
            @Override public String render() { return STR."[\{boxName}]:{\{boxContent}}"; }
        });
    }
    default PromptComponent box(String boxName, String boxContent,String  skipIfBlank) {
        if(StringUtils.isBlank(boxContent)) {
            return append(null) ;
        }
        return append(new PromptComponent() {
            @Override public String render() {
                    // 2. 注释判空：只有当 note 有字的时候，才拼接 "//"
                    // 如果 note 是 null 或 ""，suffix 就是空字符串
                    String suffix = StringUtils.isNotBlank(skipIfBlank) ? STR." // \{skipIfBlank}" : "";

                    // 3. 拼接：[Key]:{Value} // Note
                    // 注意：注释最好写在花括号外面
                    return STR."[\{boxName}]:{\{boxContent}}\{suffix}";

            }
        });
    }


    /**
     * XML 格式化组件用"<xmlName></xmlName>"包裹内容
     * @param xmlName
     * @param xmlContent
     * @return
     */
    default PromptComponent xml(String xmlName, String xmlContent) {
        if(StringUtils.isBlank(xmlContent)) {
            return append(null);
        }
        return append(
                new PromptComponent() {
            @Override
            public String render() {
                // 1. 防御性编程：处理 null
                // 2. 核心步骤：清洗
                // .strip() 会去掉字符串首尾所有的空格、换行符(\n)、制表符
                // 这样无论输入是 "内容" 还是 "\n\n内容\n"，都会变成纯净的 "内容"
                String cleanContent = xmlContent.strip();
                // 3. 统一格式化
                // 此时由你的模板全权控制间距：这里硬编码了 %n%n (一个空行)
                return String.format("<%1$s>%n%n%2$s%n%n</%1$s>", xmlName, cleanContent);
            }
        });
    }

    default PromptComponent timeSnapshot() {
        return append(new PromptComponent() {
            @Override
            public String render() {
                return STR."""
            日期和时间: \{LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}
            时间戳: \{System.currentTimeMillis()} 毫秒
            时区: Asia/Shanghai (UTC+8)
            说明: 这是系统当前时间，用于标识本次预测的运行时间
            """;
            }
        });
    }
    /**
     * dag
     */
    default PromptComponent dag() {
        return append(new PromptComponent() {
            @Override
            public String render() {
                return
                        """
    在这一阶段，你是一个专业的任务规划专家。请将用户问题分解为一个有向无环图（DAG）：
    
    要求：
    1. 每个节点代表一个原子子任务，描述必须清晰、具体、可执行；
    2. 边表示“必须先完成”的依赖关系（例如：A → B 表示 A 必须在 B 之前完成）；
    3. **绝对禁止出现循环依赖**（例如：A → B → A 是非法的）；
    4. 请按拓扑排序的顺序列出所有节点（即每个任务只能依赖它前面的任务）；
    5. **严格按以下 JSON 格式输出，不要包含任何额外解释、注释或 Markdown 语法**：
    6. 节点状态必须是“已解决”，“未解决”，“该层级暂时无法解决”三种状态之一。
    8. 第一次生成的节点都是未解决状态。
    9. 层级与问题复杂度挂钩，最高不能超过9层
    10.子层级节点只能来源于父节点,一个子节点可以链接多个父节点,一个父节点可以链接多个子节点
    11.如果该问题已经解决了，则标记解决

    {
      "nodes": [
        {"id": "唯一字符串标识", "description": "子任务描述","status":"任务状态(只可以是“已解决”，“未解决”，“该层级暂时无法解决”)","height":"当前问题的层级" }
      ],
      "edges": [
        {"from": "前置任务ID", "to": "后置任务ID"}
      ]
    }
    """
                        ;
            }
        });
    }

    /**
     * 自动提取对象属性和注解生成 Prompt
     * 只能获取第一层
     * 强烈建议使用mapSturctPlus等工具将复杂对象拆解为简单对象后再使用此方法
     * 格式：[描述/注解]: 实际值
     * 注解优先级：JsonPropertyDescription > io.swagger.v3.oas.annotations.media.Schema
     * 如果没有注解则会跳过该字段
     */
    default PromptComponentEasyFactory extractEasyBean(Object bean) {

        return append(new PromptComponent() {
            @Override
            public String render() {
                if (bean == null) return "";

                StringBuilder sb = new StringBuilder();
                // 获取类中声明的所有字段
                Field[] fields = bean.getClass().getDeclaredFields();

                for (Field field : fields) {
                    try {

                        // 某些类型（如 String, Integer 等 JDK 内部类）在 Java 21+ 模块化环境下，
                        // 调用 setAccessible(true) 会抛出 InaccessibleObjectException。
                        // 当前为了保持基础建设层的纯净，未做 instanceof 的特殊处理，
                        // 请业务层在使用 extractEasyBean 时，避免直接传入 JDK 内部类（如 String），
                        // 而是将 String 包装在自定义 Bean 中，或者使用 of() / xml() 直接输出。
                        field.setAccessible(true);
                        Object value = field.get(bean);

                        // 1. 如果值为 null，为了节省 Token，通常选择跳过（也可以改成打印 "null"）
                        if (value == null) continue;

                        // 2. 获取描述文案，优先级：JsonPropertyDescription > Schema > 字段名
                        String description = field.getName(); // 默认用字段名

                        // 检查 @JsonPropertyDescription
                        if (field.isAnnotationPresent(JsonPropertyDescription.class)) {
                            description = field.getAnnotation(JsonPropertyDescription.class).value();
                        }
                        //如果没有注解则跳过
                        else continue;

                        // 3. 拼接结果
                        sb.append(description)
                                .append(": ")
                                .append(value)
                                .append("\n");

                    } catch (IllegalAccessException e) {
                        // 忽略无法访问的字段
                    }
                }
                return sb.toString();
            }
        });
    }
    /**
     * [新增] Skill 专属：生成 Skill 的子小节
     * 对应你要求的 .skillContent("工具一", "作用")
     * 格式：
     * ## 标题
     * 内容
     */
    default PromptComponent skillSubSection(String subTitle, String content) {
        if (StringUtils.isBlank(content)) {
            return append(null);
        }
        return append(new PromptComponent() {
            @Override
            public String render() {
                // 如果没有子标题，直接输出内容；如果有，则处理成二级标题格式
                if (StringUtils.isBlank(subTitle)) {
                    return content;
                }
                return STR."""
                ## \{subTitle}

                \{content}""";
            }
        });
    }

    /**
     * [新增] 内部基础方法：生成 Skill 的头部 (YAML + H1 Title)
     * 通常由 Skill.create() 调用，不建议手动调用
     */
    default PromptComponent _internalSkillHeader(String name, String description) {
        if (StringUtils.isAnyBlank(name, description)) {
            // 这里的容错处理看你需求，或者抛出异常
            return append(null);
        }
        return append(new PromptComponent() {
            @Override
            public String render() {
                return STR."""
                 ---
                 name: \{name}
                 description: \{description}
                 ---

                 # \{name}""";
            }
        });
    }
}