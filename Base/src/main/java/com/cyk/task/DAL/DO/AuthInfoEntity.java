package com.cyk.task.DAL.DO;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.lang.Long;
import java.time.LocalDate;

import java.lang.Boolean;
import java.lang.String;
import java.lang.Integer;

/**
 * 鉴权信息表-存储各网站登录凭证及配置 实体类
 * * <p>【核心职责】</p>
 * 本实体负责管理和维护系统访问各目标网站所需的鉴权信息，包括Cookie、配置文件路径等。
 * 通过统一的鉴权池管理，实现账号资源的合理分配和故障隔离。
 * * <p>【资源池管理】</p>
 * <ul>
 * <li><b>使用计数</b>：记录每个账号被使用的次数，用于负载均衡和频率限制</li>
 * <li><b>可用性标记</b>：当Cookie失效或遇到异常时，自动标记为不可用，避免重复失败</li>
 * <li><b>独占锁</b>：在高并发场景下，防止多个任务同时使用同一账号，避免冲突</li>
 * </ul>
 * * <p>【健康检查机制】</p>
 * <ul>
 * <li><b>心跳检测</b>：定时任务验证Cookie/配置的有效性，更新last_heartbeat_time</li>
 * <li><b>故障追踪</b>：记录last_failure_time，用于分析账号稳定性</li>
 * <li><b>自动恢复</b>：心跳成功后可自动将不可用账号恢复为可用状态</li>
 * </ul>
 * * <p>【多级存储】</p>
 * <ul>
 * <li><b>本地配置</b>：config_path指向本地配置文件路径</li>
 * <li><b>云端配置</b>：cloud_storage_name支持OSS/S3等云存储，便于分布式部署</li>
 * </ul>
 * * <p>【典型使用流程】</p>
 * <pre>
 * 1. 任务创建时，从可用账号池中选择一个(is_available=true且is_exclusive=false)
 * 2. 标记为独占(is_exclusive=true)，防止并发冲突
 * 3. 增加使用计数(used_count++)
 * 4. 任务执行完成后，释放独占锁(is_exclusive=false)
 * 5. 若执行失败，标记为不可用(is_available=false)，记录失败时间
 * </pre>
 * * @author mybatis-flex-helper automatic generation
 * @since 1.0
 * @see TaskInfoEntity 任务信息表
 */
@Table(value = "auth_info")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AuthInfoEntity {

    /**
     * 鉴权表主键ID，系统自动生成的唯一标识
     */
    @Id(keyType = KeyType.Auto)
    private Long id;

    /**
     * 登录凭证Cookie的完整字符串内容，用于模拟请求或验证
     */
    @Column(value = "cookie_value")
    private String cookieValue;

    /**
     * 目标网站的名称，标明该Cookie和配置是为哪个网站准备的
     */
    @Column(value = "website_name")
    private String websiteName;

    /**
     * 配置文件的本地或相对存储路径
     */
    @Column(value = "config_path")
    private String configPath;

    /**
     * 云服务(如OSS/S3)上的存储名称或路径
     */
    @Column(value = "cloud_storage_name")
    private String cloudStorageName;

    /**
     * 该鉴权信息被使用的总次数，可用于后续的频率限制或数据统计
     */
    @Column(value = "used_count")
    private Integer usedCount;

    /**
     * 当前鉴权信息是否可用（true可用，false不可用）。例如Cookie过期、程序抛出故障时会被更新为false
     */
    @Column(value = "is_available")
    private Boolean isAvailable;

    /**
     * 是否处于独占状态（true独占，false非独占）。防止高并发下多个任务同时使用同一个账号产生冲突
     */
    @Column(value = "is_exclusive")
    private Boolean isExclusive;

    /**
     * 上次鉴权失败或接口请求报错的详细时间，用于故障追踪
     */
    @Column(value = "last_failure_time")
    private LocalDate lastFailureTime;

    /**
     * 上次定时任务（心跳检测）验证该Cookie/配置有效的具体时间
     */
    @Column(value = "last_heartbeat_time")
    private LocalDate lastHeartbeatTime;

    /**
     * 这条鉴权记录最初录入数据库的时间
     */
    @Column(value = "create_time")
    private LocalDate createTime;

    /**
     * 密码
     */
    @Column(value = "password")
    private String password;


    /**
     * 获取鉴权表主键ID
     * * @return 鉴权信息唯一标识ID
     */
    public Long getId() {
        return id;
    }

    /**
     * 设置鉴权表主键ID
     * * @param id 鉴权信息唯一标识ID
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * 获取登录凭证Cookie的完整字符串
     * * <p>Cookie值通常从浏览器开发者工具中获取，格式示例：</p>
     * <pre>
     * session_id=abc123; user_token=xyz789; expires=...
     * </pre>
     * * <p>使用时需要注意：</p>
     * <ul>
     * <li>Cookie具有时效性，需要定期更新</li>
     * <li>敏感信息，建议加密存储</li>
     * <li>部分网站Cookie可能包含IP绑定，跨机器使用可能失效</li>
     * </ul>
     * * @return Cookie完整字符串
     */
    public String getCookieValue() {
        return cookieValue;
    }

    /**
     * 设置登录凭证Cookie
     * * @param cookieValue Cookie完整字符串（建议使用抓包工具或浏览器开发者工具获取）
     */
    public void setCookieValue(String cookieValue) {
        this.cookieValue = cookieValue;
    }

    /**
     * 获取目标网站名称
     * * <p>用于标识该Cookie和配置是为哪个网站准备的，
     * 常见值：企查查、天眼查、爱企查等。</p>
     * * @return 网站名称
     */
    public String getWebsiteName() {
        return websiteName;
    }

    /**
     * 设置目标网站名称
     * * @param websiteName 网站名称（建议使用统一命名规范，如：qcc、tianyan、aqc）
     */
    public void setWebsiteName(String websiteName) {
        this.websiteName = websiteName;
    }

    /**
     * 获取配置文件的本地存储路径
     * * <p>配置文件通常包含：</p>
     * <ul>
     * <li>目标URL模板</li>
     * <li>请求头配置</li>
     * <li>数据解析规则</li>
     * <li>字段映射关系</li>
     * </ul>
     * * @return 配置文件路径（相对路径或绝对路径）
     */
    public String getConfigPath() {
        return configPath;
    }

    /**
     * 设置配置文件的本地存储路径
     * * @param configPath 配置文件路径（建议使用相对路径，如：config/qcc_pro.json）
     */
    public void setConfigPath(String configPath) {
        this.configPath = configPath;
    }

    /**
     * 获取云服务上的存储名称或路径
     * * <p>用于分布式部署场景，配置文件存储在OSS/S3等云存储上，
     * 容器启动时从云端下载配置文件。</p>
     * * @return 云存储路径（如：bucket-name/config/qcc_pro.json）
     */
    public String getCloudStorageName() {
        return cloudStorageName;
    }

    /**
     * 设置云服务上的存储名称或路径
     * * @param cloudStorageName 云存储路径
     */
    public void setCloudStorageName(String cloudStorageName) {
        this.cloudStorageName = cloudStorageName;
    }

    /**
     * 获取该鉴权信息的使用总次数
     * * <p>用途：</p>
     * <ul>
     * <li>负载均衡：优先选择使用次数少的账号</li>
     * <li>频率限制：避免单个账号使用过于频繁</li>
     * <li>统计分析：评估账号的使用价值</li>
     * </ul>
     * * @return 使用次数
     */
    public Integer getUsedCount() {
        return usedCount;
    }

    /**
     * 设置该鉴权信息的使用总次数
     * * @param usedCount 使用次数
     */
    public void setUsedCount(Integer usedCount) {
        this.usedCount = usedCount;
    }

    /**
     * 获取当前鉴权信息是否可用
     * * <p>可用性判断逻辑：</p>
     * <ul>
     * <li><b>true</b>：账号正常，可以分配给任务使用</li>
     * <li><b>false</b>：账号异常（Cookie过期、被封禁等），不应分配</li>
     * </ul>
     * * <p>不可用原因可能包括：</p>
     * <ul>
     * <li>Cookie已过期，需要重新登录</li>
     * <li>账号被目标网站封禁</li>
     * <li>连续多次请求失败</li>
     * <li>心跳检测失败</li>
     * </ul>
     * * @return true表示可用，false表示不可用
     */
    public Boolean getIsAvailable() {
        return isAvailable;
    }

    /**
     * 设置当前鉴权信息是否可用
     * * @param isAvailable 可用性标识
     */
    public void setIsAvailable(Boolean isAvailable) {
        this.isAvailable = isAvailable;
    }

    /**
     * 获取是否处于独占状态
     * * <p>独占机制用于防止高并发场景下多个任务同时使用同一账号，避免：</p>
     * <ul>
     * <li>账号被目标网站识别为异常行为</li>
     * <li>请求结果互相干扰</li>
     * <li>Cookie冲突导致鉴权失败</li>
     * </ul>
     * * <p>使用流程：</p>
     * <pre>
     * 任务开始前：is_exclusive = true（加锁）
     * 任务结束后：is_exclusive = false（释放锁）
     * 任务异常时：也要确保释放锁（finally块）
     * </pre>
     * * @return true表示已被占用，false表示可分配
     */
    public Boolean getIsExclusive() {
        return isExclusive;
    }

    /**
     * 设置是否处于独占状态
     * * <p>注意：设置独占状态时需要考虑并发安全问题，
     * 建议使用数据库的乐观锁或Redis的分布式锁。</p>
     * * @param isExclusive 独占标识
     */
    public void setIsExclusive(Boolean isExclusive) {
        this.isExclusive = isExclusive;
    }

    /**
     * 获取上次鉴权失败的时间
     * * <p>用途：</p>
     * <ul>
     * <li>故障分析：判断失败是否集中在某个时间段</li>
     * <li>自动恢复：失败后经过一定时间可尝试重新使用</li>
     * <li>监控告警：频繁失败时触发告警</li>
     * </ul>
     * * @return 上次失败时间，从未失败时为null
     */
    public LocalDate getLastFailureTime() {
        return lastFailureTime;
    }

    /**
     * 设置上次鉴权失败的时间
     * * @param lastFailureTime 失败时间
     */
    public void setLastFailureTime(LocalDate lastFailureTime) {
        this.lastFailureTime = lastFailureTime;
    }

    /**
     * 获取上次心跳检测验证成功的时间
     * * <p>心跳检测机制：</p>
     * <ul>
     * <li>定时任务定期验证Cookie的有效性</li>
     * <li>验证成功更新此时间，验证失败更新last_failure_time</li>
     * <li>长时间未心跳的账号可能已失效</li>
     * </ul>
     * * @return 上次心跳成功时间
     */
    public LocalDate getLastHeartbeatTime() {
        return lastHeartbeatTime;
    }

    /**
     * 设置上次心跳检测验证成功的时间
     * * @param lastHeartbeatTime 心跳成功时间
     */
    public void setLastHeartbeatTime(LocalDate lastHeartbeatTime) {
        this.lastHeartbeatTime = lastHeartbeatTime;
    }

    /**
     * 获取鉴权记录的创建时间
     * * @return 记录创建时间
     */
    public LocalDate getCreateTime() {
        return createTime;
    }

    /**
     * 设置鉴权记录的创建时间
     * * @param createTime 创建时间
     */
    public void setCreateTime(LocalDate createTime) {
        this.createTime = createTime;
    }

    /**
     * 获取密码
     * * @return 密码
     */
    public String getPassword() {
        return password;
    }

    /**
     * 设置密码
     * * @param password 密码
     */
    public void setPassword(String password) {
        this.password = password;
    }
}