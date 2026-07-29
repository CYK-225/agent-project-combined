package com.cyk.Utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.map.MapUtil;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Map 与 Object 转换及处理工具类
 * <p>
 * 基于 Hutool 组件封装，主要用于处理 Map 与 Java Bean 之间的动态赋值与属性提取，
 * 并提供 Map 内部键值对的条件过滤（如 null 值提取、批量 Key 删除）等便捷操作。
 * 容错率高，自带基础类型转换能力。
 * </p>
 *
 * <b>核心方法清单：</b>
 * <ul>
 * <li>{@link #fillExistingBean(Map, Object)} : 将 Map 中的值动态填充到已存在的对象中（自动忽略无法映射的报错）</li>
 * <li>{@link #getAllPropertiesMap(Object)} : 将对象的所有属性转换为 Map（保留 null 值）</li>
 * <li>{@link #getNullPropertiesMap(Object)} : 提取对象中值为 null 的属性，组装为 Map</li>
 * <li>{@link #getNonNullPropertiesMap(Object)} : 提取对象中值不为 null 的属性，组装为 Map</li>
 * <li>{@link #filterMapByNullValue(Map, boolean)} : 通用过滤方法，根据布尔值控制保留 Map 中的 null 或非 null 键值对</li>
 * <li>{@link #removeKeysFromMap(Map, List)} : 从目标 Map 中批量移除指定的 Key 列表</li>
 * <li>{@link #filterMapByKeysAndNullValue(Map , List , boolean )} :获取 Map 与指定 List 的交集，并根据布尔值过滤 null 或非 null 值</li>
 * </ul>
 *
 * @author zjhtest
 * @version 1.0
 */

public class MapObjectUtil {

    /**
     * 1. 将 Map 里的值赋值给一个已经存在的对象
     * * @param map    包含更新数据的 Map
     * @param target 已经存在的实例对象
     * @param <T>    对象类型
     * @return 填充后的对象
     */
    public static <T> T fillExistingBean(Map<String, Object> map, T target) {
        if (MapUtil.isEmpty(map) || target == null) {
            return target;
        }
        // CopyOptions.create().setIgnoreError(true) 忽略 Map 中存在但 Bean 中不存在的属性导致的报错
        return BeanUtil.fillBeanWithMap(map, target, false, CopyOptions.create().setIgnoreError(true));
    }

    /**
     * 2. 获取对象所有属性的 Map (包含 null 值)
     * * @param bean 目标对象
     * @return 包含所有属性的 Map
     */
    public static Map<String, Object> getAllPropertiesMap(Object bean) {
        if (bean == null) {
            return MapUtil.newHashMap();
        }
        // 参数说明：isToCamelCase = false (不强转驼峰), ignoreNullValue = false (保留 null 值)
        return BeanUtil.beanToMap(bean, false, false);
    }

    /**
     * 3. 获取对象中属性为 null 的 Map
     * * @param bean 目标对象
     * @return 只包含 null 值的 Map
     */
    public static Map<String, Object> getNullPropertiesMap(Object obj) {
        // 假设你前面已经把对象转成了 Map，比如叫 objMap
        Map<String, Object> objMap = BeanUtil.beanToMap(obj);

        Map<String, Object> nullProps = new HashMap<>();
        for (Map.Entry<String, Object> entry : objMap.entrySet()) {
            if (entry.getValue() == null) {
                nullProps.put(entry.getKey(), null);
            }
        }
        return nullProps;
    }

    /**
     * 4. 获取对象中属性不为 null 的 Map
     * * @param bean 目标对象
     * @return 不包含 null 值的 Map
     */
    public static Map<String, Object> getNonNullPropertiesMap(Object bean) {
        if (bean == null) {
            return MapUtil.newHashMap();
        }
        // Hutool 原生支持：isToCamelCase = false, ignoreNullValue = true (忽略 null 值)
        return BeanUtil.beanToMap(bean, false, true);
    }

    /**
     * 5. 在 Map 中删除指定 List<String> 中包含的 key
     * * @param map          目标 Map
     * @param keysToRemove 需要移除的 key 列表
     */
    public static void removeKeysFromMap(Map<String, Object> map, List<String> keysToRemove) {
        if (MapUtil.isNotEmpty(map) && CollUtil.isNotEmpty(keysToRemove)) {
            // 利用 Java 原生集合的 removeAll 方法，性能极高
            map.keySet().removeAll(keysToRemove);
        }
    }
    /**
     * 6. 根据布尔值过滤 Map 中的 null 或非 null 值
     *
     * @param map    需要过滤的原始 Map
     * @param isNull true: 只保留值为 null 的键值对; false: 只保留值不为 null 的键值对
     * @return 过滤后的新 Map
     */
    public static Map<String, Object> filterMapByNullValue(Map<String, Object> map, boolean isNull) {
        // 如果原 map 为空，直接返回一个空 map，防止空指针
        if (MapUtil.isEmpty(map)) {
            return MapUtil.newHashMap();
        }

        Map<String, Object> resultMap = MapUtil.newHashMap();

        // 遍历并过滤
        map.forEach((key, value) -> {
            // 如果 isNull 为 true，且 value 为 null，则放入 resultMap
            // 如果 isNull 为 false，且 value 不为 null，则放入 resultMap
            if (isNull ? value == null : value != null) {
                resultMap.put(key, value);
            }
        });

        return resultMap;
    }
    /**
     * 7. 获取 Map 与指定 List 的交集，并根据布尔值过滤 null 或非 null 值
     *
     * @param map        原始 Map
     * @param targetKeys 需要保留的指定 key 列表 (List)
     * @param isNull     true: 只保留值为 null 的; false: 只保留值不为 null 的
     * @return 过滤后的新 Map
     */
    public static Map<String, Object> filterMapByKeysAndNullValue(Map<String, Object> map, List<String> targetKeys, boolean isNull) {
        // 如果原始 map 或指定的 key 列表为空，直接返回空 map
        if (MapUtil.isEmpty(map) || CollUtil.isEmpty(targetKeys)) {
            return MapUtil.newHashMap();
        }

        Map<String, Object> resultMap = MapUtil.newHashMap();

        // 遍历需要保留的 key 列表
        for (String key : targetKeys) {
            // 必须使用 containsKey 判断，才能区分出 "包含该key且值为null" 和 "根本没有该key" 的区别
            if (map.containsKey(key)) {
                Object value = map.get(key);

                // 🌟 核心修改：判断值是否为 null 或者 字符串 "-"
                boolean isNullOrDash = (value == null || "-".equals(value));

                // 根据布尔值进行条件判断
                // 如果 isNull 为 true：保留 value 是 null 或 "-" 的键值对
                // 如果 isNull 为 false：保留 value 既不是 null 也不是 "-" 的键值对
                if (isNull ? isNullOrDash : !isNullOrDash) {
                    resultMap.put(key, value);
                }
            }
        }

        return resultMap;
    }
}
