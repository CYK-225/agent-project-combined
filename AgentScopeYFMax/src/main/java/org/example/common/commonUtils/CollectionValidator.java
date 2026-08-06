package org.example.common.commonUtils;

import java.util.*;

/**
 * 集合校验工具 —— 就地过滤，保证"AI挑选 ⊆ 总库"。
 * <p>
 * 核心思路：将待校验集合与白名单做交集，
 * 直接修改传入的集合，移除不在白名单中的元素，
 * 并返回被移除的元素供调用方做日志/审计。
 * <p>
 * <b>使用示例：</b>
 * <pre>{@code
 * // Map 场景：AI 选的菜品 vs 总菜品库
 * Map<Long, DishInfoAndScore> removed = CollectionValidator.retainIntersection(
 *         searchDishList,   // 待校验（会被就地修改）
 *         whiteDishMap       // 白名单
 * );
 * if (!removed.isEmpty()) {
 *     log.warn("AI 幻觉菜品已过滤: {}", removed.keySet());
 * }
 *
 * // Collection 场景：AI 选的食材名 vs 总食材库
 * Set<String> invalid = CollectionValidator.retainIntersection(
 *         aiPickedIngredients,   // 待校验（会被就地修改）
 *         totalIngredientPool    // 白名单
 * );
 * }</pre>
 *
 * @author zhilin
 * @since 2026-04-30
 */
public final class CollectionValidator {

    private CollectionValidator() {
        // 工具类，禁止实例化
    }

    // ======================== Map 按 key 做交集 ========================

    /**
     * 对 Map 做基于 key 的交集过滤（就地修改 toValidate）。
     * <p>
     * 从 {@code toValidate} 中移除所有 key <b>不在</b> {@code whitelist} 中的条目。
     * 适用于：AI 从总菜品库中挑选菜品后，校验挑选结果是否合法。
     *
     * @param toValidate 待校验的 Map（会被就地修改）
     * @param whitelist  白名单 Map，以其 key 集合作为合法值域
     * @param <K>        key 类型（通常为 Long，即菜品 ID）
     * @param <V>        value 类型
     * @return 被移除的条目（key → value），可用于日志记录或审计；为空 Map 表示全部合法
     */
    public static <K, V> Map<K, V> retainIntersection(Map<K, V> toValidate, Map<K, ?> whitelist) {
        if (toValidate == null || toValidate.isEmpty()) {
            return Collections.emptyMap();
        }
        if (whitelist == null || whitelist.isEmpty()) {
            // 白名单为空 → 全部非法，清空并返回副本
            Map<K, V> removed = new HashMap<>(toValidate);
            toValidate.clear();
            return removed;
        }

        Set<K> validKeys = whitelist.keySet();
        Map<K, V> removed = new HashMap<>();
        Iterator<Map.Entry<K, V>> it = toValidate.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<K, V> entry = it.next();
            if (!validKeys.contains(entry.getKey())) {
                removed.put(entry.getKey(), entry.getValue());
                it.remove();
            }
        }
        return removed;
    }

    // ======================== Collection 做交集 ========================

    /**
     * 对 Collection 做交集过滤（就地修改 toValidate）。
     * <p>
     * 从 {@code toValidate} 中移除所有 <b>不在</b> {@code whitelist} 中的元素。
     * 适用于：AI 从总食材名列表中挑选食材后，校验挑选结果是否合法。
     *
     * @param toValidate 待校验的集合（会被就地修改）
     * @param whitelist  白名单集合
     * @param <T>        元素类型
     * @return 被移除的元素集合，可用于日志记录或审计；为空 Set 表示全部合法
     */
    public static <T> Set<T> retainIntersection(Collection<T> toValidate, Collection<T> whitelist) {
        if (toValidate == null || toValidate.isEmpty()) {
            return Collections.emptySet();
        }
        if (whitelist == null || whitelist.isEmpty()) {
            Set<T> removed = new LinkedHashSet<>(toValidate);
            toValidate.clear();
            return removed;
        }

        Set<T> validSet = new HashSet<>(whitelist);
        Set<T> removed = new LinkedHashSet<>();
        Iterator<T> it = toValidate.iterator();
        while (it.hasNext()) {
            T item = it.next();
            if (!validSet.contains(item)) {
                removed.add(item);
                it.remove();
            }
        }
        return removed;
    }

    // ======================== 只校验不修改（只读检查） ========================

    /**
     * 检查 toCheck 的所有 key 是否都在白名单中（不修改原集合）。
     *
     * @param toCheck   待检查的 Map
     * @param whitelist 白名单 Map
     * @param <K>       key 类型
     * @return true 表示 toCheck 是 whitelist 的子集（或两者都为空）
     */
    public static <K> boolean isSubMap(Map<K, ?> toCheck, Map<K, ?> whitelist) {
        if (toCheck == null || toCheck.isEmpty()) {
            return true;
        }
        if (whitelist == null || whitelist.isEmpty()) {
            return false;
        }
        return whitelist.keySet().containsAll(toCheck.keySet());
    }

    /**
     * 检查 toCheck 的所有元素是否都在白名单中（不修改原集合）。
     *
     * @param toCheck   待检查的集合
     * @param whitelist 白名单集合
     * @param <T>       元素类型
     * @return true 表示 toCheck 是 whitelist 的子集（或两者都为空）
     */
    public static <T> boolean isSubset(Collection<T> toCheck, Collection<T> whitelist) {
        if (toCheck == null || toCheck.isEmpty()) {
            return true;
        }
        if (whitelist == null || whitelist.isEmpty()) {
            return false;
        }
        return new HashSet<>(whitelist).containsAll(toCheck);
    }
}
