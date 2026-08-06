package org.example.common.openai.hook;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * JSON 修复工具类
 * <p>
 * 处理 LLM 返回的常见非法 JSON 格式：
 * <ul>
 *   <li>markdown 代码块包裹（```json ... ```）</li>
 *   <li>尾逗号（{"a":1,}）</li>
 *   <li>单引号替双引号（{'a':'b'}）</li>
 *   <li>缺少引号的 key（{a:"b"}）</li>
 *   <li>不完整 JSON（缺少闭合括号）</li>
 * </ul>
 */
@Slf4j
public class JsonRepairUtil {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 匹配 ```json ... ``` 或 ``` ... ``` */
    private static final Pattern CODE_BLOCK_PATTERN = Pattern.compile(
            "```(?:json)?\\s*\\n?(\\{.*?})\\s*\\n?```", Pattern.DOTALL);

    /** 匹配最外层的 { ... } */
    private static final Pattern JSON_OBJECT_PATTERN = Pattern.compile(
            "(\\{.*})", Pattern.DOTALL);

    /** 匹配尾逗号：, 后面紧跟 } 或 ] */
    private static final Pattern TRAILING_COMMA = Pattern.compile(
            ",\\s*([}\\]])");

    /** 匹配单引号包裹的 key 或 value */
    private static final Pattern SINGLE_QUOTE = Pattern.compile(
            "'([^']*)'");

    /** 匹配缺少引号的 key：{ key: ... } 或 , key: ... */
    private static final Pattern UNQUOTED_KEY = Pattern.compile(
            "(?<=[{,])\\s*([a-zA-Z_][a-zA-Z0-9_]*)\\s*:");

    private JsonRepairUtil() {
    }

    /**
     * 尝试修复输入字符串，返回合法的 JSON 字符串。
     * 如果无法修复，返回 null。
     */
    public static String repair(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }

        String trimmed = input.trim();

        // 1. 如果已经是合法 JSON，直接返回
        if (isValidJson(trimmed)) {
            return trimmed;
        }

        // 2. 从 markdown 代码块提取
        String extracted = extractFromCodeBlock(trimmed);
        if (extracted != null && isValidJson(extracted)) {
            log.info("[JsonRepair] 从 markdown 代码块提取 JSON 成功");
            return extracted;
        }

        // 3. 从文本中提取 JSON 对象
        String json = extracted != null ? extracted : extractJsonObject(trimmed);
        if (json == null) {
            log.warn("[JsonRepair] 无法从输入中提取 JSON 对象，input={}", trimmed.substring(0, Math.min(200, trimmed.length())));
            return null;
        }

        // 4. 依次尝试修复
        String repaired = json;
        repaired = fixTrailingComma(repaired);
        repaired = fixSingleQuotes(repaired);
        repaired = fixUnquotedKeys(repaired);
        repaired = fixIncompleteJson(repaired);

        if (isValidJson(repaired)) {
            log.info("[JsonRepair] JSON 修复成功，原始长度={}，修复后长度={}", trimmed.length(), repaired.length());
            return repaired;
        }

        log.warn("[JsonRepair] JSON 修复失败，原始输入={}", trimmed.substring(0, Math.min(200, trimmed.length())));
        return null;
    }

    /**
     * 修复 Map 类型的 input，返回新的 Map。
     * 如果 input 已经是合法 Map，直接返回。
     * 如果 input 是 String 类型（未解析），尝试修复后解析。
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> repairInput(Object input) {
        if (input instanceof Map) {
            return (Map<String, Object>) input;
        }
        if (input instanceof String str) {
            String repaired = repair(str);
            if (repaired != null) {
                try {
                    return MAPPER.readValue(repaired, Map.class);
                } catch (JsonProcessingException e) {
                    log.error("[JsonRepair] 修复后的 JSON 仍无法解析: {}", repaired, e);
                }
            }
        }
        return null;
    }

    // ==================== 内部方法 ====================

    private static boolean isValidJson(String str) {
        try {
            MAPPER.readTree(str);
            return true;
        } catch (JsonProcessingException e) {
            return false;
        }
    }

    private static String extractFromCodeBlock(String input) {
        Matcher m = CODE_BLOCK_PATTERN.matcher(input);
        if (m.find()) {
            return m.group(1).trim();
        }
        return null;
    }

    private static String extractJsonObject(String input) {
        Matcher m = JSON_OBJECT_PATTERN.matcher(input);
        if (m.find()) {
            return m.group(1).trim();
        }
        return null;
    }

    private static String fixTrailingComma(String json) {
        return TRAILING_COMMA.matcher(json).replaceAll("$1");
    }

    private static String fixSingleQuotes(String json) {
        // 简单替换：将单引号替换为双引号
        // 注意：这会破坏包含单引号的值内容，但对于工具参数通常没问题
        return SINGLE_QUOTE.matcher(json).replaceAll("\"$1\"");
    }

    private static String fixUnquotedKeys(String json) {
        return UNQUOTED_KEY.matcher(json).replaceAll("\"$1\":");
    }

    private static String fixIncompleteJson(String json) {
        // 计算括号平衡
        int braceCount = 0;
        int bracketCount = 0;
        boolean inString = false;
        char prev = 0;

        for (char c : json.toCharArray()) {
            if (c == '"' && prev != '\\') {
                inString = !inString;
            } else if (!inString) {
                if (c == '{') braceCount++;
                else if (c == '}') braceCount--;
                else if (c == '[') bracketCount++;
                else if (c == ']') bracketCount--;
            }
            prev = c;
        }

        StringBuilder sb = new StringBuilder(json);
        // 补齐缺失的闭合括号
        while (bracketCount > 0) {
            sb.append(']');
            bracketCount--;
        }
        while (braceCount > 0) {
            sb.append('}');
            braceCount--;
        }

        return sb.toString();
    }
}
