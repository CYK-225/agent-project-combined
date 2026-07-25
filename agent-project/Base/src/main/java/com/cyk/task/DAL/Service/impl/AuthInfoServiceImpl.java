package com.cyk.task.DAL.Service.impl;


import com.cyk.task.DAL.DO.AuthInfoEntity;
import com.cyk.task.DAL.Mapper.AuthInfoMapper;
import com.cyk.task.DAL.Service.IAuthInfoService;
import com.mybatisflex.core.query.QueryMethods;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.update.UpdateChain;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mybatisflex.spring.service.impl.ServiceImpl;

import java.time.LocalDate;
import java.util.List;

/**
 * 鉴权信息表-存储各网站登录凭证及配置 服务层实现
 * 
 * <p>【核心职责】</p>
 * 本服务实现类负责鉴权信息池的管理，实现账号选择、独占管理、健康检查等业务逻辑。
 * 
 * <p>【并发安全】</p>
 * 账号选择和独占管理涉及并发问题，本实现使用数据库的CAS机制确保线程安全：
 * <ul>
 *   <li>acquireAccount方法使用乐观锁（WHERE is_exclusive=false）</li>
 *   <li>如果加锁失败，自动重试最多3次</li>
 *   <li>确保在高并发场景下不会出现多个任务使用同一账号的情况</li>
 * </ul>
 * 
 * <p>【使用建议】</p>
 * <ul>
 *   <li>任务执行前调用acquireAccount获取账号</li>
 *   <li>任务执行后务必调用releaseAccount释放锁（建议在finally块中）</li>
 *   <li>发现账号异常时立即调用markAsUnavailable标记</li>
 *   <li>配置定时任务定期调用getAccountsNeedHeartbeat进行健康检查</li>
 * </ul>
 * 
 * @author mybatis-flex-helper automatic generation
 * @since 1.0
 * @see IAuthInfoService 鉴权信息服务接口
 * @see AuthInfoMapper 鉴权信息Mapper接口
 * @see AuthInfoEntity 鉴权信息实体类
 */
@Slf4j
@Service
public class AuthInfoServiceImpl extends ServiceImpl<AuthInfoMapper, AuthInfoEntity> implements IAuthInfoService {

    /**
     * 账号加锁的最大重试次数
     */
    private static final int MAX_RETRY_COUNT = 3;

    /**
     * 从指定网站的账号池中选择并锁定一个账号
     * 
     * <p>实现细节：</p>
     * <ol>
     *   <li>selectAvailableAccount方法查询最优账号</li>
     *   <li>调用Mapper的acquireExclusiveLock方法使用CAS加锁</li>
     *   <li>如果加锁失败，重试最多3次</li>
     *   <li>加锁成功后，增加使用计数</li>
     * </ol>
     * 
     * <p>并发场景示例：</p>
     * <pre>
     * 线程A和线程B同时尝试获取账号：
     * 1. 都查询到账号123（used_count=10）
     * 2. 线程A执行CAS加锁 → 成功（影响1行）
     * 3. 线程B执行CAS加锁 → 失败（影响0行）
     * 4. 线程B重试，查询到账号456（used_count=15）
     * 5. 线程B执行CAS加锁 → 成功
     * </pre>
     * 
     * @param websiteName 网站名称
     * @return 账号信息，如果没有可用账号则返回null
     */

    @Resource
    protected AuthInfoMapper authInfoMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AuthInfoEntity acquireAccount(String websiteName) {
        int retryCount = 0;
        
        while (retryCount < MAX_RETRY_COUNT) {
            // 1. 查询最优账号
            AuthInfoEntity account = selectAvailableAccount(websiteName);
            if (account == null) {
                // 没有可用账号
                return null;
            }
            
            // 2. 尝试加锁（CAS操作）
            int lockResult =  acquireExclusiveLock(account.getId());
            if (lockResult > 0) {
                // 加锁成功
                // 3. 增加使用计数
                 incrementUsedCount(account.getId());
                return account;
            }
            
            // 加锁失败，说明账号已被其他线程抢占，重试
            retryCount++;
        }
        
        // 重试次数用尽，返回null
        return null;
    }

    /**
     * 释放账号的独占锁
     * 
     * <p>实现细节：</p>
     * <ol>
     *   <li>调用Mapper的releaseExclusiveLock方法释放锁</li>
     *   <li>将is_exclusive设置为false</li>
     * </ol>
     * 
     * @param authId 鉴权信息ID
     * @return 是否成功释放
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean releaseAccount(Long authId) {
        int result =  releaseExclusiveLock(authId);
        return result > 0;
    }
    /**
     * 以非独占的方式，获取可用的非独占的且尽可能次数少的账号
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public AuthInfoEntity getAccountByNoExclusive(String websiteName){
        return mapper.selectOneByQuery(
                QueryWrapper.create()
                        .select(AuthInfoEntity::getId,AuthInfoEntity::getCloudStorageName,AuthInfoEntity::getCloudStorageName)
                        .from(AuthInfoEntity.class)
                        .eq(AuthInfoEntity::getWebsiteName,websiteName)
                        .eq(AuthInfoEntity::getIsAvailable, true)
                        .eq(AuthInfoEntity::getIsExclusive, false)
                        .orderBy(AuthInfoEntity::getUsedCount, true)
                        .limit(1)
        );
    }

    /**
     * 标记账号为不可用
     * 
     * <p>实现细节：</p>
     * <ol>
     *   <li>调用Mapper的updateAvailability方法</li>
     *   <li>设置is_available=false</li>
     *   <li>记录last_failure_time</li>
     * </ol>
     * 
     * <p>标记为不可用后，需要人工介入或心跳检测成功后才能恢复。</p>
     * 
     * @param authId 鉴权信息ID
     * @param reason 不可用原因（用于记录日志）
     * @return 是否成功标记
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean markAsUnavailable(Long authId, String reason) {
        int result =  updateAvailability(authId, false);
        
        // 可以在这里记录日志或发送告警
        if (result > 0) {
            // 记录日志：账号被标记为不可用
            AuthInfoEntity auth = this.getById(authId);
            if (auth != null) {
                System.out.println(String.format(
                    "[账号不可用] ID: %d | 网站: %s | 原因: %s",
                    authId, auth.getWebsiteName(), reason
                ));
                mapper.update(AuthInfoEntity.builder()
                                .id(authId)
                                .lastFailureTime(LocalDate.now())
                                .isAvailable(false)
                        .build());
            }
        }
        
        return result > 0;
    }

    /**
     * 更新心跳检测时间
     * 
     * <p>实现细节：</p>
     * <ol>
     *   <li>调用Mapper的updateHeartbeat方法</li>
     *   <li>更新last_heartbeat_time</li>
     *   <li>根据isValid参数更新is_available</li>
     * </ol>
     * 
     * @param authId 鉴权信息ID
     * @param isValid Cookie是否有效
     * @return 是否成功更新
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateHeartbeat(Long authId, boolean isValid) {
        int result =  updateHeartbeatMapper(authId, isValid);
        return result > 0;
    }

    /**
     * 获取指定网站的可用账号数量
     * 
     * <p>调用Mapper的countAvailableAccounts方法，
     * 统计is_available=true且is_exclusive=false的账号数量。</p>
     * 
     * @param websiteName 网站名称
     * @return 可用账号数量
     */
    @Override
    public int countAvailableAccounts(String websiteName) {
        return  countAvailableAccounts(websiteName);
    }

    /**
     * 获取需要心跳检测的账号列表
     * 
     * <p>调用Mapper的selectAccountsNeedHeartbeat方法，
     * 查询长时间未进行心跳检测的账号。</p>
     * 
     * @param hours 未心跳的小时数阈值
     * @return 需要检测的账号列表
     */
    @Override
    public List<AuthInfoEntity> getAccountsNeedHeartbeat(int hours) {
        return  selectAccountsNeedHeartbeat(hours);
    }

    /**
     * 获取账号（优先使用用户指定的配置）
     * 
     * <p>优先级逻辑：</p>
     * <ol>
     *   <li>如果用户指定了 preferredConfigName，优先尝试获取对应的可用账号</li>
     *   <li>如果用户指定的配置不可用或未指定，则从账号池中自动选择</li>
     * </ol>
     * 
     * <p>返回结果说明：</p>
     * <ul>
     *   <li>如果 preferredConfigName 不为空且成功获取到对应账号，返回该账号</li>
     *   <li>如果 preferredConfigName 为空或对应账号不可用，返回自动选择的账号</li>
     *   <li>如果没有可用账号，返回 null</li>
     * </ul>
     * 
     * <p>日志记录：</p>
     * <ul>
     *   <li>记录是否使用了用户指定的配置</li>
     *   <li>记录最终使用的配置来源</li>
     * </ul>
     * 
     * @param websiteName 网站名称
     * @param preferredConfigName 用户指定的配置名称（可为null）
     * @param isExclusive 是否独占模式
     * @return 账号信息，如果没有可用账号则返回null
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public AuthInfoEntity acquireAccountWithPriority(String websiteName, String preferredConfigName, boolean isExclusive) {
        AuthInfoEntity auth = null;
        boolean usedPreferredConfig = false;
        
        // 1. 如果用户指定了配置名称，优先尝试获取
        if (preferredConfigName != null && !preferredConfigName.trim().isEmpty()) {
            auth = tryAcquireByConfigName(preferredConfigName, isExclusive);
            log.info(STR."auth\{auth}");
            if (auth != null) {
                usedPreferredConfig = true;
                log.info("[配置选择] 使用用户指定配置: configName={}, authId={}, website={}",
                        preferredConfigName, auth.getId(), websiteName);
            } else {
                log.info("[配置选择] 用户指定配置不可用: configName={}, website={}, 尝试自动选择",
                        preferredConfigName, websiteName);
            }
        }
        
        // 2. 如果用户指定的配置不可用或未指定，从账号池自动选择
        if (auth == null) {
            if (isExclusive) {
                auth = acquireAccount(websiteName);
            } else {
                auth = getAccountByNoExclusive(websiteName);
            }
            
            if (auth != null) {
                log.info(STR."auth\{auth}");
                log.info("[配置选择] 使用自动选择配置: authId={}, website={}, 来源={}",
                        auth.getId(), websiteName, 
                        preferredConfigName != null ? "用户指定不可用，自动选择" : "用户未指定，自动选择");
            }
        }
        
        // 3. 最终结果日志
        if (auth != null) {
            log.info("[配置选择] 最终结果: authId={}, cloudStorageName={}, usedPreferredConfig={}",
                    auth.getId(), auth.getCloudStorageName(), usedPreferredConfig);
        } else {
            log.warn("[配置选择] 无可用账号: website={}, preferredConfigName={}",
                    websiteName, preferredConfigName);
        }
        
        return auth;
    }
    
    /**
     * 尝试根据配置名称获取账号
     * 
     * @param configName 配置名称（cloudStorageName）
     * @param isExclusive 是否独占模式
     * @return 账号信息，如果不可用则返回null
     */
    private AuthInfoEntity tryAcquireByConfigName(String configName, boolean isExclusive) {
        // 查询指定配置的可用账号
        AuthInfoEntity auth = mapper.selectOneByQuery(
                QueryWrapper.create()
                        .from(AuthInfoEntity.class)
                        .eq(AuthInfoEntity::getCloudStorageName, configName)
                        .eq(AuthInfoEntity::getIsAvailable, true)
                        .eq(AuthInfoEntity::getIsExclusive, false)
                        .limit(1)
        );
        
        if (auth == null) {
            return null;
        }
        
        // 如果是独占模式，需要加锁
        if (isExclusive) {
            int lockResult = acquireExclusiveLock(auth.getId());
            if (lockResult <= 0) {
                // 加锁失败，说明已被其他任务占用
                return null;
            }
            // 加锁成功，增加使用计数
            incrementUsedCount(auth.getId());
        }
        
        return auth;
    }
    /**
     * 从指定网站的可用账号池中选择一个最优账号
     *
     * <p>选择策略：</p>
     * <ul>
     *   <li><b>可用性</b>：is_available = true</li>
     *   <li><b>非独占</b>：is_exclusive = false</li>
     *   <li><b>负载均衡</b>：优先选择used_count少的账号</li>
     * </ul>
     *
     * <p>使用场景：</p>
     * <pre>
     * 任务创建时：
     * 1. 调用此方法选择一个账号
     * 2. 调用acquireExclusiveLock()加锁
     * 3. 执行任务
     * 4. 调用releaseExclusiveLock()释放锁
     * </pre>
     *
     * @return 最优账号信息，如果没有可用账号则返回null
     */

    @Override
    public int releaseAllExclusiveLocks() {
        AuthInfoEntity updateEntity = new AuthInfoEntity();
        updateEntity.setIsExclusive(false);
        return mapper.updateByQuery(updateEntity, QueryWrapper.create()
                .where(AuthInfoEntity::getIsExclusive).eq(true));
    }

    @Override
    public Long selectAuthIdByConfigNameAndWebsiteName(String configName, String websiteName){
        // 1. 查询实体
        AuthInfoEntity entity = authInfoMapper.selectOneByQuery(QueryWrapper.create()
                .from(AuthInfoEntity.class)
                .where(AuthInfoEntity::getCloudStorageName).eq(configName)
                .and(AuthInfoEntity::getWebsiteName).eq(websiteName)
                .limit(1));

        // 2. 安全判空，避免直接 .getId() 导致 NPE
        return entity != null ? entity.getId() : null;
    }

    private AuthInfoEntity selectAvailableAccount( String websiteName){
        return mapper.selectOneByQuery(QueryWrapper.create()
                .from(AuthInfoEntity.class)
                .where(AuthInfoEntity::getWebsiteName).eq(websiteName)
                .eq(AuthInfoEntity::getIsAvailable,true)
                .eq(AuthInfoEntity::getIsExclusive,false)
                .orderBy(AuthInfoEntity::getUsedCount,true)
                .limit(1)
        );
    }
    /**
     * 获取账号的独占锁（CAS操作）
     *
     * <p>使用Compare-And-Swap机制确保并发安全：
     * 只有当账号当前未被占用（is_exclusive=false）时，才能成功加锁。</p>
     *
     * <p>并发场景示例：</p>
     * <pre>
     * 线程A和线程B同时尝试获取账号123的锁：
     * - 初始状态：is_exclusive = false
     * - 线程A执行UPDATE WHERE is_exclusive=false → 成功（影响1行）
     * - 线程B执行UPDATE WHERE is_exclusive=false → 失败（影响0行）
     * </pre>
     *
     * @param authId 鉴权信息ID
     * @return 加锁是否成功（1=成功，0=失败）
     */
    private int acquireExclusiveLock(Long authId) {
        AuthInfoEntity updateEntity = new AuthInfoEntity();
        updateEntity.setIsExclusive(true);
        return mapper.updateByQuery(updateEntity, QueryWrapper.create()
                .where(AuthInfoEntity::getId).eq(authId)
                .and(AuthInfoEntity::getIsExclusive).eq(false));
    }

    /**
     * 释放账号的独占锁
     *
     * <p>任务执行完成后，必须释放锁，否则账号将一直被占用。
     * 建议在finally块中调用此方法，确保锁一定会被释放。</p>
     *
     * <p>示例代码：</p>
     * <pre>
     * try {
     *     // 执行任务
     * } finally {
     *     authInfoMapper.releaseExclusiveLock(authId);
     * }
     * </pre>
     *
     * @param authId 鉴权信息ID
     * @return 释放是否成功（1=成功，0=账号不存在或已被删除）
     */
    private int releaseExclusiveLock(Long authId) {
        AuthInfoEntity updateEntity = new AuthInfoEntity();
        updateEntity.setIsExclusive(false);
        return mapper.updateByQuery(updateEntity, QueryWrapper.create()
                .where(AuthInfoEntity::getId).eq(authId));
    }

    /**
     * 增加账号的使用计数
     *
     * <p>每次任务使用该账号时，调用此方法增加计数，
     * 用于负载均衡和使用统计。</p>
     *
     * @param authId 鉴权信息ID
     * @return 更新的记录数
     */
    private int incrementUsedCount(Long authId) {
        // 使用 UpdateChain 方便地执行 SET 字段 = 字段 + 1 的自增操作
        return UpdateChain.of(AuthInfoEntity.class)
                .setRaw(AuthInfoEntity::getUsedCount, "used_count + 1")
                .where(AuthInfoEntity::getId).eq(authId)
                .update() ? 1 : 0;
    }


    /**
     * 标记账号为不可用（鉴权失败时调用）
     *
     * <p>当任务执行过程中发现Cookie失效、账号被封禁等异常时，
     * 调用此方法标记账号为不可用，避免后续任务继续使用失败账号。</p>
     *
     * <p>标记为不可用后，需要人工介入或心跳检测成功后才能恢复。</p>
     *
     * @param authId 鉴权信息ID
     * @param isAvailable 可用性标识（false=不可用）
     * @return 更新的记录数
     */
    private int updateAvailability(Long authId, Boolean isAvailable) {
        return UpdateChain.of(AuthInfoEntity.class)
                .set(AuthInfoEntity::getIsAvailable, isAvailable)
                .setRaw(AuthInfoEntity::getLastFailureTime, "CURRENT_TIMESTAMP")
                .where(AuthInfoEntity::getId).eq(authId)
                .update() ? 1 : 0;
    }

    /**
     * 更新心跳检测时间
     *
     * <p>定时任务验证Cookie有效性后，调用此方法更新心跳时间。
     * 如果验证成功，还可以将账号恢复为可用状态。</p>
     *
     * @param authId 鉴权信息ID
     * @param isAvailable 是否可用（验证成功为true，失败为false）
     * @return 更新的记录数
     */
    private int updateHeartbeatMapper(Long authId, Boolean isAvailable) {
        return UpdateChain.of(AuthInfoEntity.class)
                .set(AuthInfoEntity::getIsAvailable, isAvailable)
                .setRaw(AuthInfoEntity::getLastHeartbeatTime, "CURRENT_TIMESTAMP")
                .where(AuthInfoEntity::getId).eq(authId)
                .update() ? 1 : 0;
    }

    /**
     * 根据云存储名称查询可用的账号
     *
     * <p>用于用户指定配置时，优先使用用户指定的配置文件。</p>
     *
     * <p>查询条件：</p>
     * <ul>
     *   <li>cloud_storage_name 匹配</li>
     *   <li>is_available = true</li>
     *   <li>is_exclusive = false</li>
     * </ul>
     *
     * @param cloudStorageName 云存储名称（配置文件名）
     * @return 账号信息，如果没有可用账号则返回null
     */
    private AuthInfoEntity selectAvailableByCloudStorageName(String cloudStorageName) {
        return mapper.selectOneByQuery(QueryWrapper.create()
                .where(AuthInfoEntity::getCloudStorageName).eq(cloudStorageName)
                .and(AuthInfoEntity::getIsAvailable).eq(true)
                .and(AuthInfoEntity::getIsExclusive).eq(false)
                .limit(1));
    }
    /**
     * 查询所有需要心跳检测的账号
     *
     * <p>定时任务定期检查账号健康状态，优先检查长时间未心跳的账号。</p>
     *
     * @param hours 未心跳的小时数阈值（如：超过24小时未心跳）
     * @return 需要检测的账号列表
     */

    private List<AuthInfoEntity> selectAccountsNeedHeartbeat( int hours){
// 最后传入 mapper 执行
       return  mapper.selectListByQuery( QueryWrapper.create()
               // 对应: WHERE last_heartbeat_time IS NULL
               .where(AuthInfoEntity::getLastHeartbeatTime).isNull()

               // 对应: OR last_heartbeat_time < CURRENT_TIMESTAMP - INTERVAL '#{hours} hours'
               .or(AuthInfoEntity::getLastHeartbeatTime).lt(
                       QueryMethods.raw(STR."CURRENT_TIMESTAMP - INTERVAL '\{hours} hours'")
               )

               // 对应: ORDER BY last_heartbeat_time ASC NULLS FIRST
               .orderBy(AuthInfoEntity::getLastFailureTime,true));

    }

    /**
     * 更新鉴权信息状态
     *
     * <p>任务执行完成后，需要更新鉴权信息状态，
     * 以便后续任务使用。</p>
     *
     * <p>示例代码：</p>
     * <pre>
     * authInfoService.updateAuthStatus(profileName, targetUrl, true);
     * </pre>
     *
     * @param profileName 配置文件名
     * @param targetUrl 目标URL
     * @param isAvailable 是否可用（true=可用，false=不可用）
     **/
    @Override
    public void updateAuthStatus(String profileName, String targetUrl, boolean isAvailable) {
        // 完全沿用你的 MyBatis-Flex 查询风格
        QueryWrapper query = QueryWrapper.create()
                .where(AuthInfoEntity::getCloudStorageName).eq(profileName)
                .where(AuthInfoEntity::getWebsiteName).eq(targetUrl);

        AuthInfoEntity authInfo = authInfoMapper.selectOneByQuery(query);

        if (authInfo != null) {
            // 已存在，只更新可用状态
            authInfo.setIsAvailable(isAvailable);
            // 使用 MyBatis-Flex 的自带更新方法
            authInfoMapper.update(authInfo);
        } else {
            // 不存在，执行插入
            AuthInfoEntity newAuthInfo = AuthInfoEntity.builder()
                    .cloudStorageName(profileName)
                    .websiteName(targetUrl)
                    .isAvailable(isAvailable)
                    .build();
            // 使用 MyBatis-Flex 的自带插入方法
            authInfoMapper.insert(newAuthInfo);
        }
    }

    /**
     * 获取所有配置文件列表
     *
     * <p>用于获取所有配置文件列表，
     * 以便用户查看和选择配置文件。</p>
     *
     * <p>示例代码：</p>
     * <pre>
     * List&lt;AuthInfoEntity&gt; profileList = authInfoService.getProfileList();
     * </pre>
     *
     * @return 配置文件列表
     */
    @Override
    public List<AuthInfoEntity> getProfileList() {
        // 使用 MyBatis-Flex 查询所有记录
        QueryWrapper query = QueryWrapper.create()
                // 排除被逻辑删除或确实不需要的数据（如果有的话）
                // .where(AuthInfoEntity::getIsDeleted).eq(0)
                // 按配置名排序，保证同名的数据靠在一起
                .orderBy(AuthInfoEntity::getCloudStorageName, true);

        // 返回扁平的实体列表
        return authInfoMapper.selectListByQuery(query);
    }




}