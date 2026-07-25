package org.example.common.validator;/**
 * @Auter zzh
 * @Date 2025/10/13
 */

/**
 * @projectName: AItest
 * @package: org.example.aitest.infrastructure.Utils
 * @className: a
 * @author: Eric
 * @description: TODO
 * @date: 2025/10/13 22:38
 * @version: 1.0
 */

import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * 通用函数式校验器，支持链式调用，适用于任意实体类。
 * 无需注解，无需 Spring，仅依赖 JDK 8。
 *
 * <h3>使用示例：</h3>
 * <pre>{@code
 * User user = new User();
 *
 * // 方式一：校验失败直接抛出异常
 * FluentValidator.of(user)
 * .requireNotNull(user.getName(), "用户名")
 * .requireMobile(user.getPhone(), "手机号")
 * .requireRange(user.getAge(), 18, 60, "年龄")
 * .check(u -> u.getBalance() > 0, "余额必须大于0")
 * .throwIfInvalid();
 *
 * // 方式二：获取错误列表手动处理
 * FluentValidator<User> validator = FluentValidator.of(user)
 * .requireEmail(user.getEmail(), "邮箱");
 *
 * if (!validator.isValid()) {
 * List<String> errors = validator.getErrors();
 * // 处理错误...
 * }
 *
 * // 方式三：抛出自定义异常
 * FluentValidator.of(user)
 * .requireNonBlank(user.getName(), "用户名")
 * .throwCustomException(msg -> new BusinessException(400, msg));
 * }</pre>
 *
 * @param <T> 被校验对象的类型（通常为实体类）
 * @author Eric
 * @version 1.0
 */
public class FluentValidator<T> {
    private final T target;
    private final List<String> errors = new ArrayList<>();

    private FluentValidator(T target) {
        this.target = target;
    }

    // ==================== 特定校验方法 ====================
    /**
     * 價格校验
     */
    public   FluentValidator<T>  requirePrice(Double price, String fieldName) {
        return checkField(price, v -> v != null && v >= 0 && v <= 1000000,
                String.format(STR."\{fieldName}必须在0到1000000之间"));
    }

    /**
     * 创建校验器实例。
     *
     * @param target 待校验的对象
     * @param <T>    对象类型
     * @return FluentValidator 实例
     */
    public static <T> FluentValidator<T> of(T target) {
        return new FluentValidator<>(target);
    }

    // ==================== 通用校验方法 ====================

    /**
     * 自定义条件校验。
     *
     * @param condition    校验条件（返回 true 表示合法）
     * @param errorMessage 错误提示信息
     * @return 当前校验器实例，支持链式调用
     */
    public FluentValidator<T> check(Predicate<T> condition, String errorMessage) {
        if (!condition.test(target)) {
            errors.add(errorMessage);
        }
        return this;
    }

    /**
     * 对某个字段值进行自定义条件校验。
     *
     * @param fieldValue   字段值
     * @param condition    校验条件
     * @param errorMessage 错误提示信息
     * @param <F>          字段类型
     * @return 当前校验器实例
     */
    public <F> FluentValidator<T> checkField(F fieldValue, Predicate<F> condition, String errorMessage) {
        if (!condition.test(fieldValue)) {
            errors.add(errorMessage);
        }
        return this;
    }

    // ==================== 基础校验 ====================

    /**
     * 校验字符串是否为合法的正数（必须 > 0，支持整数和小数，不能含负号、汉字、字母等）。
     * 合法示例： "1", "0.5", "123.45"
     * 非法示例： "0", "-1", "abc", "12.5.6", "一二三", null, ""
     */
    public FluentValidator<T> requirePositiveNumber(String value, String fieldName) {
        return checkField(value, FluentValidator::isValidPositiveNumber, fieldName + "必须是大于0的正数");
    }

    /**
     * 校验字符串是否为合法的数字格式（整数或小数，可含正负号，但不能有其他字符）。
     * 合法示例： "123", "-45.67", "0.5", "+3.14"
     * 非法示例： "12a", "12.5.6", "一二三", "12 34", ""
     */
    public FluentValidator<T> requireNumeric(String value, String fieldName) {
        return checkField(value, FluentValidator::isValidNumeric, fieldName + "必须是有效的数字（例如：123 或 -45.67）");
    }

    /**
     * 校验字符串是否为合法的整数格式（可含正负号，但不能有小数点或其他字符）。
     * 合法示例： "123", "-456", "+789"
     * 非法示例： "12.5", "12a", "一二三", ""
     */
    public FluentValidator<T> requireIntegerString(String value, String fieldName) {
        return checkField(value, FluentValidator::isValidIntegerString, fieldName + "必须是有效的整数（例如：-123）");
    }

    /**
     * 校验字段是否为 null。
     *
     * @param value     字段值
     * @param fieldName 字段名称（用于错误提示）
     * @return 当前校验器实例
     */
    public FluentValidator<T> requireNotNull(Object value, String fieldName) {
        return checkField(value, v -> v != null, fieldName + "不能为空");
    }

    /**
     * 校验字符串是否为 null 或空（""）。
     *
     * @param value     字符串值
     * @param fieldName 字段名称
     * @return 当前校验器实例
     */
    public FluentValidator<T> requireNonEmpty(String value, String fieldName) {
        return checkField(value, v -> v != null && !v.isEmpty(), fieldName + "不能为空");
    }

    /**
     * 校验字符串是否为 null、空或仅包含空白字符。
     *
     * @param value     字符串值
     * @param fieldName 字段名称
     * @return 当前校验器实例
     */
    public FluentValidator<T> requireNonBlank(String value, String fieldName) {
        return checkField(value, v -> v != null && !v.trim().isEmpty(), fieldName + "不能为null、空或仅包含空白字符");
    }

    /**
     * 校验字符串长度是否在指定范围内（包含边界）。
     *
     * @param value     字符串值
     * @param min       最小长度
     * @param max       最大长度
     * @param fieldName 字段名称
     * @return 当前校验器实例
     */
    public FluentValidator<T> requireLengthBetween(String value, int min, int max, String fieldName) {
        return checkField(value, v -> v != null && v.length() >= min && v.length() <= max,
                String.format("%s长度必须在%d到%d之间", fieldName, min, max));
    }

    // ==================== 数值校验 ====================

    /**
     * 校验整数是否 ≥ 指定最小值。
     */
    public FluentValidator<T> requireMin(int value, int min, String fieldName) {
        return checkField(value, v -> v >= min, String.format("%s不能小于%d", fieldName, min));
    }

    /**
     * 校验整数是否 ≤ 指定最大值。
     */
    public FluentValidator<T> requireMax(int value, int max, String fieldName) {
        return checkField(value, v -> v <= max, String.format("%s不能大于%d", fieldName, max));
    }

    /**
     * 校验整数是否在 [min, max] 范围内。
     */
    public FluentValidator<T> requireRange(int value, int min, int max, String fieldName) {
        return checkField(value, v -> v >= min && v <= max,
                String.format("%s必须在%d到%d之间", fieldName, min, max));
    }

    /**
     * 校验浮点数是否 ≥ 指定最小值。
     */
    public FluentValidator<T> requireMin(double value, double min, String fieldName) {
        return checkField(value, v -> v >= min, String.format("%s不能小于%.2f", fieldName, min));
    }

    /**
     * 校验浮点数是否 ≤ 指定最大值。
     */
    public FluentValidator<T> requireMax(double value, double max, String fieldName) {
        return checkField(value, v -> v <= max, String.format("%s不能大于%.2f", fieldName, max));
    }

    /**
     * 校验浮点数是否在 [min, max] 范围内。
     */
    public FluentValidator<T> requireRange(double value, double min, double max, String fieldName) {
        return checkField(value, v -> v >= min && v <= max,
                String.format("%s必须在%.2f到%.2f之间", fieldName, min, max));
    }

    /**
     * 校验是否为正整数（> 0）。
     */
    public FluentValidator<T> requirePositiveInteger(Integer value, String fieldName) {
        return checkField(value, v -> v != null && v > 0, fieldName + "必须是正整数");
    }

    /**
     * 校验是否为非负整数（≥ 0）。
     */
    public FluentValidator<T> requireNonNegativeInteger(Integer value, String fieldName) {
        return checkField(value, v -> v != null && v >= 0, fieldName + "必须是非负整数");
    }

    // ==================== 常用格式校验 ====================

    /**
     * 校验是否为合法邮箱地址。
     */
    public FluentValidator<T> requireEmail(String email, String fieldName) {
        return checkField(email, FluentValidator::isValidEmail, fieldName + "不是有效的邮箱地址");
    }

    /**
     * 校验是否为中国大陆手机号（支持 13/14/15/17/18/19 开头的 11 位号码）。
     */
    public FluentValidator<T> requireMobile(String mobile, String fieldName) {
        return checkField(mobile, FluentValidator::isValidMobile, fieldName + "不是有效的手机号码");
    }

    /**
     * 校验是否为合法的中国大陆身份证号（15 位或 18 位，18 位含校验码）。
     */
    public FluentValidator<T> requireIdCard(String idCard, String fieldName) {
        return checkField(idCard, FluentValidator::isValidIdCard, fieldName + "不是有效的身份证号码");
    }

    /**
     * 校验是否为合法 URL（协议 + 域名/ IP + 可选端口/路径）。
     */
    public FluentValidator<T> requireUrl(String url, String fieldName) {
        return checkField(url, FluentValidator::isValidUrl, fieldName + "不是有效的URL地址");
    }

    /**
     * 校验是否为合法 IPv4 地址（如 192.168.1.1）。
     */
    public FluentValidator<T> requireIpv4(String ip, String fieldName) {
        return checkField(ip, FluentValidator::isValidIpv4, fieldName + "不是有效的IPv4地址");
    }

    /**
     * 校验是否为合法 IPv6 地址（如 ::1, 2001:db8::1）。
     */
    public FluentValidator<T> requireIpv6(String ip, String fieldName) {
        return checkField(ip, FluentValidator::isValidIpv6, fieldName + "不是有效的IPv6地址");
    }

    /**
     * 校验是否为合法 IP 地址（支持 IPv4 或 IPv6）。
     */
    public FluentValidator<T> requireIp(String ip, String fieldName) {
        return checkField(ip, v -> isValidIpv4(v) || isValidIpv6(v), fieldName + "不是有效的IP地址");
    }

    /**
     * 校验是否为合法中文姓名（2~20 个汉字，可含·用于少数民族姓名）。
     */
    public FluentValidator<T> requireChineseName(String name, String fieldName) {
        return checkField(name, FluentValidator::isValidChineseName, fieldName + "必须是2~20个汉字（例如：张三 或 迪丽热巴·迪力木拉提）");
    }

    /**
     * 校验是否为合法邮政编码（6 位数字）。
     */
    public FluentValidator<T> requirePostcode(String postcode, String fieldName) {
        return checkField(postcode, FluentValidator::isValidPostcode, fieldName + "不是有效的邮政编码（应为6位数字）");
    }

    /**
     * 校验是否为合法金额格式（非负数，最多两位小数，如 100、100.5、100.50）。
     */
    public FluentValidator<T> requireAmount(Double amount, String fieldName) {
        return checkField(amount, FluentValidator::isValidAmount, fieldName + "不是有效的金额格式（例如：100.50）");
    }

    /**
     * 校验是否为合法经度（-180到180度，支持小数点后最多15位）。
     */
    public FluentValidator<T> requireLongitude(String longitude, String fieldName) {
        return checkField(longitude, FluentValidator::isValidLongitude, fieldName + "不是有效的经度（范围为-180到180，例如：116.4074）");
    }

    /**
     * 校验是否为合法纬度（-90到90度，支持小数点后最多15位）。
     */
    public FluentValidator<T> requireLatitude(String latitude, String fieldName) {
        return checkField(latitude, FluentValidator::isValidLatitude, fieldName + "不是有效的纬度（范围为-90到90，例如：39.9042）");
    }

    /**
     * 校验字符串中不包含任何中文汉字（允许英文、数字、符号、空格等，但不能有中文）。
     * 适用于编码、标识符、技术字段等不允许中文的场景。
     */
    public FluentValidator<T> requireNoChineseCharacters(String value, String fieldName) {
        return checkField(value, v -> v == null || !containsChinese(v), fieldName + "不能包含中文字符");
    }

    /**
     * 使用正则表达式校验字符串。
     *
     * @param value     字符串值
     * @param regex     正则表达式
     * @param fieldName 字段名
     * @return 当前校验器实例
     */
    public FluentValidator<T> requirePattern(String value, String regex, String fieldName) {
        return checkField(value, v -> v != null && Pattern.matches(regex, v),
                fieldName + "不符合指定格式");
    }

    // ==================== 私有校验逻辑 ====================

    private static final String EMAIL_REGEX =
            "^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$";
    private static final String MOBILE_REGEX = "^1[3-9]\\d{9}$";
    private static final String URL_REGEX =
            "^https?://(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,}(?::\\d{1,5})?(?:/[\\w.-]*)*(?:\\?[\\w&=%.-]*)?(?:#[\\w.-]*)?$";
    private static final String IPV4_REGEX =
            "^(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$";
    private static final String IPV6_REGEX =
            "^([0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}$|" +
                    "^([0-9a-fA-F]{1,4}:){1,7}:$|" +
                    "^:([0-9a-fA-F]{1,4}:){1,6}[0-9a-fA-F]{1,4}$|" +
                    "^([0-9a-fA-F]{1,4}:){1,6}:$|" +
                    "^::1$";
    private static final String CHINESE_NAME_REGEX = "^[\\u4e00-\\u9fa5]{2,20}(?:·[\\u4e00-\\u9fa5]{2,20})*$";
    private static final String POSTCODE_REGEX = "^[1-9]\\d{5}$";
    private static final String AMOUNT_REGEX = "^[0-9]+(\\.\\d{1,2})?$";
    private static final String LONGITUDE_REGEX = "^-?1(8[0]|[1-7]\\d)\\.(\\d{1,15})$|^-?1(8[0]|[1-7]\\d)$|^-?0(\\.[\\d]{1,15})?$|^0$";
    private static final String LATITUDE_REGEX = "^-?9[0]\\.(\\d{1,15})$|^-(9[0]|[1-8]\\d)\\.(\\d{1,15})$|^9[0]$|^-(9[0]|[1-8]\\d)$";

    private static boolean isValidEmail(String email) {
        return email != null && Pattern.compile(EMAIL_REGEX).matcher(email).matches();
    }

    private static boolean isValidMobile(String mobile) {
        return mobile != null && Pattern.compile(MOBILE_REGEX).matcher(mobile).matches();
    }

    private static boolean isValidUrl(String url) {
        return url != null && Pattern.compile(URL_REGEX).matcher(url).matches();
    }

    private static boolean isValidIpv4(String ip) {
        return ip != null && Pattern.compile(IPV4_REGEX).matcher(ip).matches();
    }

    private static boolean isValidIpv6(String ip) {
        return ip != null && Pattern.compile(IPV6_REGEX).matcher(ip).matches();
    }

    private static boolean isValidChineseName(String name) {
        return name != null && Pattern.compile(CHINESE_NAME_REGEX).matcher(name).matches();
    }

    private static boolean isValidPostcode(String postcode) {
        return postcode != null && Pattern.compile(POSTCODE_REGEX).matcher(postcode).matches();
    }

    private static boolean isValidAmount(Double amount) {
        if (amount == null) {
            return false;
        }
        if (amount < 0) {
            return false;
        }
        String str = String.format("%.2f", amount).replaceAll("0*$", "").replaceAll("\\.$", "");
        return Pattern.compile(AMOUNT_REGEX).matcher(str).matches();
    }

    /**
     * 身份证校验（支持 15 位和 18 位，18 位含校验码）。
     */
    private static boolean isValidIdCard(String idCard) {
        if (idCard == null) {
            return false;
        }
        idCard = idCard.trim();
        if (idCard.length() == 15) {
            return Pattern.matches("^[1-9]\\d{7}((0\\d)|(1[0-2]))(([0|1|2]\\d)|3[0-1])\\d{3}$", idCard);
        } else if (idCard.length() == 18) {
            String body = idCard.substring(0, 17);
            char checkBit = idCard.charAt(17);
            if (!Pattern.matches("^[1-9]\\d{5}[1-2]\\d{3}((0\\d)|(1[0-2]))(([0|1|2]\\d)|3[0-1])\\d{3}$", body)) {
                return false;
            }
            // 校验码计算
            int[] wi = {7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2};
            char[] checkCodes = {'1', '0', 'X', '9', '8', '7', '6', '5', '4', '3', '2'};
            int sum = 0;
            for (int i = 0; i < 17; i++) {
                sum += (body.charAt(i) - '0') * wi[i];
            }
            char expected = checkCodes[sum % 11];
            return expected == checkBit || (expected == 'X' && checkBit == 'x');
        }
        return false;
    }

    /**
     * 判断字符串是否为合法的数字（整数或浮点数），支持正负号。
     * 使用 Double.parseDouble 安全解析，避免正则复杂性。
     */
    private static boolean isValidNumeric(String str) {
        if (str == null || str.isEmpty()) {
            return false;
        }
        try {
            Double.parseDouble(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * 判断字符串是否为合法的整数（不包含小数点）。
     * 使用 Long.parseLong 避免溢出误判（比 Integer 范围更宽，但仍是整数语义）。
     */
    private static boolean isValidIntegerString(String str) {
        if (str == null || str.isEmpty()) {
            return false;
        }
        try {
            // 先检查是否包含小数点（避免 "123.0" 被误认为整数）
            if (str.contains(".") || str.contains("e") || str.contains("E")) {
                return false;
            }
            // 使用 Long 避免部分大整数被误判为非法
            Long.parseLong(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * 判断字符串是否包含中文汉字（基于 Unicode 基本汉字区块）。
     */
    private static boolean containsChinese(String str) {
        if (str == null || str.isEmpty()) {
            return false;
        }
        return str.chars().anyMatch(c -> c >= 0x4E00 && c <= 0x9FFF);
    }

    private static boolean isValidPositiveNumber(String str) {
        if (str == null || str.isEmpty()) {
            return false;
        }
        // 禁止包含 e/E（科学计数法）
        if (str.contains("e") || str.contains("E")) {
            return false;
        }
        // 必须是标准数字格式（可含 . 和 +/-，但 Double 会处理）
        try {
            double num = Double.parseDouble(str);
            return num > 0 && !Double.isInfinite(num) && !Double.isNaN(num);
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * 校验是否为合法经度（-180到180度，支持小数点后最多15位）。
     */
    private static boolean isValidLongitude(String longitude) {
        if (longitude == null || longitude.isEmpty()) {
            return false;
        }
        try {
            double lon = Double.parseDouble(longitude);
            return lon >= -180 && lon <= 180;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * 校验是否为合法纬度（-90到90度，支持小数点后最多15位）。
     */
    private static boolean isValidLatitude(String latitude) {
        if (latitude == null || latitude.isEmpty()) {
            return false;
        }
        try {
            double lat = Double.parseDouble(latitude);
            return lat >= -90 && lat <= 90;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    // ==================== 结果处理 ====================

    /**
     * 判断是否所有校验都通过。
     *
     * @return true 表示无错误
     */
    public boolean isValid() {
        return errors.isEmpty();
    }

    /**
     * 获取所有校验错误信息。
     *
     * @return 错误列表（副本）
     */
    public List<String> getErrors() {
        return new ArrayList<>(errors);
    }

    /**
     * 如果存在校验错误，则抛出 IllegalArgumentException。
     */
    public void throwIfInvalid() {
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(String.join("; ", errors));
        }
    }

    /**
     * 如果存在校验错误，则抛出异常，使用自定义异常（根据自己系统修改）
     */
    public void throwSelfDefineException() {
        if (!errors.isEmpty()) {
            throw new RuntimeException(String.join("; ", errors));
        }
    }
    /**
     * 如果存在校验错误，则抛出自定义异常。
     * <p>
     * 使用示例：
     * <pre>{@code
     *       .throwCustomException(msg -> new BusinessException(400, msg));
     *       // 或者使用方法引用
     *       .throwCustomException(IllegalArgumentException::new);
     * }

     * </pre>
     *
     * @param exceptionFactory 异常工厂函数，接收错误信息字符串，返回一个异常实例
     * @param <X>              异常类型（支持 RuntimeException 和 Checked Exception）
     * @throws X 当校验失败时抛出
     */
    public <X extends Throwable> void throwCustomException(java.util.function.Function<String, X> exceptionFactory) throws X {
        if (!errors.isEmpty()) {
            // 将所有错误信息拼接成一个字符串
            String errorMessage = String.join("; ", errors);
            // 调用工厂函数创建异常并抛出
            throw exceptionFactory.apply(errorMessage);
        }
    }
}