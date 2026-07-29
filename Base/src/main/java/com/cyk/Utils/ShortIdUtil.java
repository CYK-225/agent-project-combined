package com.cyk.Utils;

import lombok.extern.slf4j.Slf4j;

/**
 * 53位精简版雪花算法工具类 (单机版)
 * 特点：
 * 1. 生成的 ID 绝对不超过 16 位纯数字 (最大值 9007199254740991)
 * 2. 完美适配前端 JS 的 Number 类型，绝不丢失精度
 * 3. 趋势递增，非常适合作为 MySQL 数据库主键
 * 4. 单机每秒可生成约 200 万个唯一 ID
 */
@Slf4j
public class ShortIdUtil {

    // ============================== 核心配置 ==============================
    
    // 自定义纪元时间戳 (这里设定为 2024-01-01 00:00:00 的毫秒数)
    // 作用：减小时间戳的值，让可用年限从 2024 年开始往后算 69 年
    private static final long EPOCH = 1704067200000L;

    // 序列号占用的位数: 11位 (单机每毫秒最多生成 2048 个 ID)
    private static final long SEQUENCE_BITS = 11L;

    // 序列号的最大值: 2047 (通过位运算计算得到)
    private static final long MAX_SEQUENCE = ~(-1L << SEQUENCE_BITS);

    // ============================== 运行状态 ==============================
    
    private static long sequence = 0L;
    private static long lastTimestamp = -1L;

    /**
     * 私有化构造函数，防止外部实例化
     */
    private ShortIdUtil() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * 生成下一个唯一的纯数字 ID (核心方法)
     * 加入了 synchronized 保证多线程并发下的绝对安全
     *
     * @return 16位以内的纯数字 ID
     */
    public static synchronized long nextId() {

        log.info("生成唯一的纯数字 ID");
        long currentTimestamp = System.currentTimeMillis();

        // 校验系统时钟是否回拨
        if (currentTimestamp < lastTimestamp) {
            throw new RuntimeException(String.format(
                    "系统时钟发生回拨！拒绝为接下来的 %d 毫秒生成 ID", lastTimestamp - currentTimestamp));
        }

        // 如果在同一毫秒内生成，则序列号递增
        if (currentTimestamp == lastTimestamp) {
            sequence = (sequence + 1) & MAX_SEQUENCE;
            // 如果同一毫秒内的序列号用完了 (超过2047)，阻塞等待下一毫秒
            if (sequence == 0) {
                currentTimestamp = waitNextMillis(lastTimestamp);
            }
        } else {
            // 不同毫秒，序列号重置为 0
            sequence = 0L;
        }

        lastTimestamp = currentTimestamp;

        // 核心位运算拼接：(当前时间 - 纪元时间) 左移 11 位 | 序列号
        return ((currentTimestamp - EPOCH) << SEQUENCE_BITS) | sequence;
    }

    /**
     * 生成下一个唯一的纯数字 ID，并转为字符串
     * 便于某些明确要求 String 类型的业务场景直接调用
     *
     * @return 16位以内的纯数字字符串
     */
    public static String nextIdStr() {
        return String.valueOf(nextId());
    }

    /**
     * 阻塞到下一毫秒，直到获得新的时间戳
     *
     * @param lastTimestamp 上次生成 ID 的时间截
     * @return 当前时间戳
     */
    private static long waitNextMillis(long lastTimestamp) {
        long timestamp = System.currentTimeMillis();
        while (timestamp <= lastTimestamp) {
            timestamp = System.currentTimeMillis();
        }
        return timestamp;
    }
}