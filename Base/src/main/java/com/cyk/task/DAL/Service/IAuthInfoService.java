package com.cyk.task.DAL.Service;



import com.cyk.task.DAL.DO.AuthInfoEntity;
import com.mybatisflex.core.service.IService;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 鉴权信息表-存储各网站登录凭证及配置 服务层接口
 * 
 * <p>【核心职责】</p>
 * 本服务接口负责管理鉴权信息池，提供账号选择、独占管理、健康检查等功能，
 * 是任务执行器获取鉴权信息的统一入口。
 * 
 * <p>【账号池管理】</p>
 * <ul>
 *   <li><b>账号选择</b>：从可用账号池中选择最优账号（使用次数少、非独占）</li>
 *   <li><b>独占锁管理</b>：加锁/解锁账号，防止并发冲突</li>
 *   <li><b>使用计数</b>：记录账号使用次数，用于负载均衡</li>
 * </ul>
 * 
 * <p>【健康检查】</p>
 * <ul>
 *   <li><b>心跳检测</b>：定期验证Cookie有效性</li>
 *   <li><b>故障标记</b>：Cookie失效时标记为不可用</li>
 *   <li><b>自动恢复</b>：心跳成功后恢复账号可用性</li>
 * </ul>
 * 
 * <p>【使用示例】</p>
 * <pre>
 * // 注入服务
 * {@literal @}Autowired
 * private IAuthInfoService authInfoService;
 * 
 * // 选择账号并加锁
 * AuthInfoEntity auth = authInfoService.acquireAccount("qcc");
 * if (auth != null) {
 *     try {
 *         // 使用账号执行任务
 *         String cookie = auth.getCookieValue();
 *         // ...
 *     } finally {
 *         // 释放锁
 *         authInfoService.releaseAccount(auth.getId());
 *     }
 * }
 * </pre>
 * 
 * @author mybatis-flex-helper automatic generation
 * @since 1.0
 * @see AuthInfoEntity 鉴权信息实体类
 */
public interface IAuthInfoService extends IService<AuthInfoEntity> {

    /**
     * 从指定网站的账号池中选择并锁定一个账号
     * 
     * <p>这是一个原子操作，包含以下步骤：</p>
     * <ol>
     *   <li>查询可用账号（is_available=true, is_exclusive=false）</li>
     *   <li>使用CAS机制加锁（防止并发冲突）</li>
     *   <li>增加使用计数</li>
     *   <li>返回账号信息</li>
     * </ol>
     * 
     * <p>如果加锁失败，会自动重试（最多3次），确保一定能选到账号。</p>
     * 
     * @param websiteName 网站名称
     * @return 账号信息，如果没有可用账号则返回null
     */
    AuthInfoEntity acquireAccount(String websiteName);

    /**
     * 释放账号的独占锁
     * 
     * <p>任务执行完成后必须调用此方法释放锁，否则账号将一直被占用。
     * 建议在finally块中调用，确保锁一定会被释放。</p>
     * 
     * @param authId 鉴权信息ID
     * @return 是否成功释放
     */
    boolean releaseAccount(Long authId);



    @Transactional(rollbackFor = Exception.class)
    AuthInfoEntity getAccountByNoExclusive(String websiteName);

    /**
     * 标记账号为不可用
     * 
     * <p>当任务执行过程中发现Cookie失效、账号被封禁等异常时，
     * 调用此方法标记账号为不可用。</p>
     * 
     * @param authId 鉴权信息ID
     * @param reason 不可用原因
     * @return 是否成功标记
     */
    boolean markAsUnavailable(Long authId, String reason);

    /**
     * 更新心跳检测时间
     * 
     * <p>定时任务验证Cookie有效性后，调用此方法更新心跳时间。
     * 如果验证成功，账号会被标记为可用；如果验证失败，账号会被标记为不可用。</p>
     * 
     * @param authId 鉴权信息ID
     * @param isValid Cookie是否有效
     * @return 是否成功更新
     */
    boolean updateHeartbeat(Long authId, boolean isValid);

    /**
     * 获取指定网站的可用账号数量
     * 
     * <p>用于监控和告警，当可用账号数量低于阈值时触发告警。</p>
     * 
     * @param websiteName 网站名称
     * @return 可用账号数量
     */
    int countAvailableAccounts(String websiteName);

    /**
     * 获取需要心跳检测的账号列表
     * 
     * <p>定时任务定期检查账号健康状态，优先检查长时间未心跳的账号。</p>
     * 
     * @param hours 未心跳的小时数阈值
     * @return 需要检测的账号列表
     */
    List<AuthInfoEntity> getAccountsNeedHeartbeat(int hours);

    /**
     * 获取账号（优先使用用户指定的配置）
     * 
     * <p>优先级逻辑：</p>
     * <ol>
     *   <li>如果用户指定了 configName，优先尝试获取对应的可用账号</li>
     *   <li>如果用户指定的配置不可用或未指定，则从账号池中自动选择</li>
     * </ol>
     * 
     * @param websiteName 网站名称
     * @param preferredConfigName 用户指定的配置名称（可为null）
     * @param isExclusive 是否独占模式
     * @return 账号信息，以及是否使用了用户指定配置的标记
     */
    AuthInfoEntity acquireAccountWithPriority(String websiteName, String preferredConfigName, boolean isExclusive);

    Long selectAuthIdByConfigNameAndWebsiteName(String configName, String websiteName);

    int releaseAllExclusiveLocks();

    void updateAuthStatus(String profileName, String targetUrl, boolean isAvailable);

    List<AuthInfoEntity> getProfileList();
}