package com.cyk.task.DAL.Mapper;

import com.cyk.task.DAL.DO.AuthInfoEntity;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 鉴权信息表-存储各网站登录凭证及配置 映射层
 * 
 * <p>【核心职责】</p>
 * 本Mapper接口负责auth_info表的数据库访问操作，提供鉴权信息的增删改查能力，
 * 支持账号资源池的管理、健康检查和负载均衡。
 * 
 * <p>【继承能力】</p>
 * 通过继承BaseMapper&lt;AuthInfoEntity&gt;，自动拥有基础的CRUD能力，
 * 本接口额外提供鉴权池管理相关的业务方法。
 * 
 * <p>【业务方法】</p>
 * <ul>
 *   <li><b>账号选择</b>：从可用账号池中选择一个最优账号（使用次数少、非独占）</li>
 *   <li><b>独占管理</b>：加锁/解锁账号，防止并发冲突</li>
 *   <li><b>健康检查</b>：更新心跳时间、标记不可用账号</li>
 *   <li><b>统计查询</b>：统计各网站的可用账号数量</li>
 * </ul>
 * 
 * <p>【并发安全】</p>
 * 账号选择和独占管理涉及并发问题，建议配合数据库乐观锁或Redis分布式锁使用：
 * <pre>
 * 1. 查询可用账号（is_available=true, is_exclusive=false）
 * 2. 使用CAS更新is_exclusive=true（WHERE is_exclusive=false）
 * 3. 如果更新成功，说明抢到了锁；否则重试
 * </pre>
 * 
 * @author mybatis-flex-helper automatic generation
 * @since 1.0
 * @see AuthInfoEntity 鉴权信息实体类
 */
@Mapper
public interface AuthInfoMapper extends BaseMapper<AuthInfoEntity> {

//
//
//    /**
//     * 获取账号的独占锁（CAS操作）
//     *
//     * <p>使用Compare-And-Swap机制确保并发安全：
//     * 只有当账号当前未被占用（is_exclusive=false）时，才能成功加锁。</p>
//     *
//     * <p>并发场景示例：</p>
//     * <pre>
//     * 线程A和线程B同时尝试获取账号123的锁：
//     * - 初始状态：is_exclusive = false
//     * - 线程A执行UPDATE WHERE is_exclusive=false → 成功（影响1行）
//     * - 线程B执行UPDATE WHERE is_exclusive=false → 失败（影响0行）
//     * </pre>
//     *
//     * @param authId 鉴权信息ID
//     * @return 加锁是否成功（1=成功，0=失败）
//     */
//    @Update("UPDATE auth_info SET is_exclusive = true " +
//            "WHERE id = #{authId} AND is_exclusive = false")
//    int acquireExclusiveLock(@Param("authId") Long authId);
//
//    /**
//     * 释放账号的独占锁
//     *
//     * <p>任务执行完成后，必须释放锁，否则账号将一直被占用。
//     * 建议在finally块中调用此方法，确保锁一定会被释放。</p>
//     *
//     * <p>示例代码：</p>
//     * <pre>
//     * try {
//     *     // 执行任务
//     * } finally {
//     *     authInfoMapper.releaseExclusiveLock(authId);
//     * }
//     * </pre>
//     *
//     * @param authId 鉴权信息ID
//     * @return 释放是否成功（1=成功，0=账号不存在或已被删除）
//     */
//    @Update("UPDATE auth_info SET is_exclusive = false WHERE id = #{authId}")
//    int releaseExclusiveLock(@Param("authId") Long authId);
//
//    /**
//     * 增加账号的使用计数
//     *
//     * <p>每次任务使用该账号时，调用此方法增加计数，
//     * 用于负载均衡和使用统计。</p>
//     *
//     * @param authId 鉴权信息ID
//     * @return 更新的记录数
//     */
//    @Update("UPDATE auth_info SET used_count = used_count + 1 WHERE id = #{authId}")
//    int incrementUsedCount(@Param("authId") Long authId);
//
//    /**
//     * 标记账号为不可用（鉴权失败时调用）
//     *
//     * <p>当任务执行过程中发现Cookie失效、账号被封禁等异常时，
//     * 调用此方法标记账号为不可用，避免后续任务继续使用失败账号。</p>
//     *
//     * <p>标记为不可用后，需要人工介入或心跳检测成功后才能恢复。</p>
//     *
//     * @param authId 鉴权信息ID
//     * @param isAvailable 可用性标识（false=不可用）
//     * @return 更新的记录数
//     */
//    @Update("UPDATE auth_info SET " +
//            "is_available = #{isAvailable}, " +
//            "last_failure_time = CURRENT_TIMESTAMP " +
//            "WHERE id = #{authId}")
//    int updateAvailability(@Param("authId") Long authId,
//                          @Param("isAvailable") Boolean isAvailable);
//
//    /**
//     * 更新心跳检测时间
//     *
//     * <p>定时任务验证Cookie有效性后，调用此方法更新心跳时间。
//     * 如果验证成功，还可以将账号恢复为可用状态。</p>
//     *
//     * @param authId 鉴权信息ID
//     * @param isAvailable 是否可用（验证成功为true，失败为false）
//     * @return 更新的记录数
//     */
//    @Update("UPDATE auth_info SET " +
//            "last_heartbeat_time = CURRENT_TIMESTAMP, " +
//            "is_available = #{isAvailable} " +
//            "WHERE id = #{authId}")
//    int updateHeartbeat(@Param("authId") Long authId,
//                       @Param("isAvailable") Boolean isAvailable);
//
//    /**
//     * 统计指定网站的可用账号数量
//     *
//     * <p>用于监控和告警：当可用账号数量低于阈值时，触发告警通知运维人员补充账号。</p>
//     *
//     * @param websiteName 网站名称
//     * @return 可用账号数量
//     */
//    @Select("SELECT COUNT(*) FROM auth_info " +
//            "WHERE website_name = #{websiteName} " +
//            "AND is_available = true " +
//            "AND is_exclusive = false")
//    int countAvailableAccounts(@Param("websiteName") String websiteName);
//
//    /**
//     * 查询所有需要心跳检测的账号
//     *
//     * <p>定时任务定期检查账号健康状态，优先检查长时间未心跳的账号。</p>
//     *
//     * @param hours 未心跳的小时数阈值（如：超过24小时未心跳）
//     * @return 需要检测的账号列表
//     */
//    @Select("SELECT * FROM auth_info " +
//            "WHERE last_heartbeat_time IS NULL " +
//            "OR last_heartbeat_time < CURRENT_TIMESTAMP - INTERVAL '#{hours} hours' " +
//            "ORDER BY last_heartbeat_time ASC NULLS FIRST")
//    List<AuthInfoEntity> selectAccountsNeedHeartbeat(@Param("hours") int hours);
//
//    /**
//     * 根据云存储名称查询可用的账号
//     *
//     * <p>用于用户指定配置时，优先使用用户指定的配置文件。</p>
//     *
//     * <p>查询条件：</p>
//     * <ul>
//     *   <li>cloud_storage_name 匹配</li>
//     *   <li>is_available = true</li>
//     *   <li>is_exclusive = false</li>
//     * </ul>
//     *
//     * @param cloudStorageName 云存储名称（配置文件名）
//     * @return 账号信息，如果没有可用账号则返回null
//     */
//    @Select("SELECT * FROM auth_info " +
//            "WHERE cloud_storage_name = #{cloudStorageName} " +
//            "AND is_available = true " +
//            "AND is_exclusive = false " +
//            "LIMIT 1")
//    AuthInfoEntity selectAvailableByCloudStorageName(@Param("cloudStorageName") String cloudStorageName);

}
