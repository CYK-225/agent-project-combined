package com.cyk.DockerTool.CookieGet.cmd;

import lombok.Data;

@Data
public class CookieTaskConfig {
    /**
     * 任务 ID
     */
    private String taskId;
    /**
     * 站点：fengniao, aiqicha
     */
    private String site;
    /**
     * 账号
     */
    private String account;
    /**
     * 密码
     */
    private String password;
    /**
     * 回调 URL
     */
    private String callbackUrl;
    
    /**
     * 运行模式：
     * "fetch" -> 读写模式 (RW)，无则登录，有则验证，失败重试并写入
     * "heartbeat" -> 只读模式 (RO)，仅验证复制过来的 Cookie 是否有效，绝不写入宿主机
     */
    private String mode = "fetch"; 
}