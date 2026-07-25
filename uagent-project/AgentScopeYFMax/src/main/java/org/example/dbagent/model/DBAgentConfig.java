package org.example.dbagent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DBAgent配置请求
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DBAgentConfig {
    /** 数据库类型：auto/mysql/postgresql */
    private String dbType;
    /** MySQL主机 */
    private String mysqlHost;
    /** MySQL端口 */
    private Integer mysqlPort;
    /** MySQL数据库名 */
    private String mysqlDatabase;
    /** MySQL用户名 */
    private String mysqlUser;
    /** MySQL密码 */
    private String mysqlPassword;
    /** Git仓库地址 */
    private String gitRepoUrl;
    /** Git分支名称 */
    private String gitBranch;
    /** Git用户名 */
    private String gitUsername;
    /** Git Token */
    private String gitToken;
    /** 代码生成基础包名 */
    private String basePackage;
    /** 框架模板：mybatis-flex/mybatis-plus */
    private String framework;
}
