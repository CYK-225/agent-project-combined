package org.example.dbagent.service;

import lombok.extern.slf4j.Slf4j;
import org.example.dbagent.model.DBAgentConfig;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;

/**
 * Git操作服务
 * 负责Git仓库的克隆、拉取、提交、推送等操作
 */
@Slf4j
@Service
public class GitService {

    @Value("${dbagent.git.repo-url:}")
    private String defaultRepoUrl;

    @Value("${dbagent.git.branch:feature/ai-dao}")
    private String defaultBranch;

    @Value("${dbagent.git.local-path:./workspace/dbagent-repo}")
    private String localPath;

    @Value("${dbagent.git.username:}")
    private String defaultUsername;

    @Value("${dbagent.git.token:}")
    private String defaultToken;

    /**
     * 使用前端配置获取Git仓库实例
     */
    private Git getGit(DBAgentConfig config) throws GitAPIException, IOException {
        String repoUrl = config.getGitRepoUrl() != null ? config.getGitRepoUrl() : defaultRepoUrl;
        String branch = config.getGitBranch() != null ? config.getGitBranch() : defaultBranch;

        Path gitPath = Paths.get(localPath);
        File gitDir = gitPath.resolve(".git").toFile();

        if (gitDir.exists()) {
            return Git.open(gitPath.toFile());
        }

        // 克隆仓库
        log.info("克隆仓库: {} -> {}", repoUrl, localPath);
        var cloneCommand = Git.cloneRepository()
                .setURI(repoUrl)
                .setDirectory(gitPath.toFile())
                .setBranch(branch);

        CredentialsProvider cloneCp = getCredentialsProvider(config);
        if (cloneCp != null) {
            cloneCommand.setCredentialsProvider(cloneCp);
        }

        return cloneCommand.call();
    }

    /**
     * 使用默认配置获取Git仓库实例
     */
    private Git getGit() throws GitAPIException, IOException {
        Path gitPath = Paths.get(localPath);
        File gitDir = gitPath.resolve(".git").toFile();

        if (gitDir.exists()) {
            return Git.open(gitPath.toFile());
        }

        // 克隆仓库
        log.info("克隆仓库: {} -> {}", defaultRepoUrl, localPath);
        var cloneCommand = Git.cloneRepository()
                .setURI(defaultRepoUrl)
                .setDirectory(gitPath.toFile())
                .setBranch(defaultBranch);

        CredentialsProvider defCp = getCredentialsProvider();
        if (defCp != null) {
            cloneCommand.setCredentialsProvider(defCp);
        }

        return cloneCommand.call();
    }

    /**
     * 使用前端配置拉取最新代码
     */
    public void pullLatest(DBAgentConfig config) {
        String repoUrl = config.getGitRepoUrl() != null ? config.getGitRepoUrl() : defaultRepoUrl;
        if (repoUrl == null || repoUrl.isBlank()) {
            log.warn("Git仓库未配置，跳过拉取");
            return;
        }

        try (Git git = getGit(config)) {
            String branch = config.getGitBranch() != null ? config.getGitBranch() : defaultBranch;

            // 切换到目标分支
            checkoutBranch(git, branch);

            // 拉取最新代码
            var pullCmd = git.pull()
                    .setRemoteBranchName(branch);
            CredentialsProvider cp = getCredentialsProvider(config);
            if (cp != null) {
                pullCmd.setCredentialsProvider(cp);
            }
            var pullResult = pullCmd.call();

            if (pullResult.isSuccessful()) {
                log.info("拉取成功: {}", branch);
            } else {
                log.warn("拉取可能有问题: {}", pullResult.getMergeResult().getMergeStatus());
            }
        } catch (Exception e) {
            log.error("拉取代码失败", e);
            throw new RuntimeException("拉取代码失败: " + e.getMessage(), e);
        }
    }

    /**
     * 使用默认配置拉取最新代码
     */
    public void pullLatest() {
        if (defaultRepoUrl == null || defaultRepoUrl.isBlank()) {
            log.warn("Git仓库未配置，跳过拉取");
            return;
        }

        try (Git git = getGit()) {
            // 切换到目标分支
            checkoutBranch(git, defaultBranch);

            // 拉取最新代码
            var defPullCmd = git.pull()
                    .setRemoteBranchName(defaultBranch);
            CredentialsProvider defPullCp = getCredentialsProvider();
            if (defPullCp != null) {
                defPullCmd.setCredentialsProvider(defPullCp);
            }
            var pullResult = defPullCmd.call();

            if (pullResult.isSuccessful()) {
                log.info("拉取成功: {}", defaultBranch);
            } else {
                log.warn("拉取可能有问题: {}", pullResult.getMergeResult().getMergeStatus());
            }
        } catch (Exception e) {
            log.error("拉取代码失败", e);
            throw new RuntimeException("拉取代码失败: " + e.getMessage(), e);
        }
    }

    /**
     * 使用前端配置提交并推送
     */
    public void commitAndPush(DBAgentConfig config, String tableName, List<String> changes) {
        String repoUrl = config.getGitRepoUrl() != null ? config.getGitRepoUrl() : defaultRepoUrl;
        if (repoUrl == null || repoUrl.isBlank()) {
            log.warn("Git仓库未配置，跳过提交推送");
            return;
        }

        try (Git git = getGit(config)) {
            String branch = config.getGitBranch() != null ? config.getGitBranch() : defaultBranch;

            // 切换到目标分支
            checkoutBranch(git, branch);

            // 添加所有变更
            git.add().addFilepattern(".").call();

            // 构建提交信息
            String commitMessage = buildCommitMessage(tableName, changes);

            // 提交
            git.commit()
                    .setMessage(commitMessage)
                    .call();

            log.info("提交成功: {}", commitMessage);

            // 推送
            var pushCmd = git.push();
            CredentialsProvider pushCp = getCredentialsProvider(config);
            if (pushCp != null) {
                pushCmd.setCredentialsProvider(pushCp);
            }
            pushCmd.call();

            log.info("推送成功: {}", branch);

        } catch (Exception e) {
            log.error("提交推送失败", e);
            throw new RuntimeException("提交推送失败: " + e.getMessage(), e);
        }
    }

    /**
     * 使用默认配置提交并推送
     */
    public void commitAndPush(String tableName, List<String> changes) {
        if (defaultRepoUrl == null || defaultRepoUrl.isBlank()) {
            log.warn("Git仓库未配置，跳过提交推送");
            return;
        }

        try (Git git = getGit()) {
            // 切换到目标分支
            checkoutBranch(git, defaultBranch);

            // 添加所有变更
            git.add().addFilepattern(".").call();

            // 构建提交信息
            String commitMessage = buildCommitMessage(tableName, changes);

            // 提交
            git.commit()
                    .setMessage(commitMessage)
                    .call();

            log.info("提交成功: {}", commitMessage);

            // 推送
            var defPushCmd = git.push();
            CredentialsProvider defPushCp = getCredentialsProvider();
            if (defPushCp != null) {
                defPushCmd.setCredentialsProvider(defPushCp);
            }
            defPushCmd.call();

            log.info("推送成功: {}", defaultBranch);

        } catch (Exception e) {
            log.error("提交推送失败", e);
            throw new RuntimeException("提交推送失败: " + e.getMessage(), e);
        }
    }

    /**
     * 切换分支
     */
    private void checkoutBranch(Git git, String branchName) throws GitAPIException {
        // 检查分支是否存在
        List<Ref> branches = git.branchList().call();
        boolean branchExists = branches.stream()
                .anyMatch(ref -> ref.getName().equals("refs/heads/" + branchName));

        if (branchExists) {
            git.checkout().setName(branchName).call();
        } else {
            // 创建并切换到新分支
            git.checkout()
                    .setCreateBranch(true)
                    .setName(branchName)
                    .call();
        }
    }

    /**
     * 构建提交信息
     */
    private String buildCommitMessage(String tableName, List<String> changes) {
        StringBuilder sb = new StringBuilder();
        sb.append("feat(dao): 自动更新 ").append(tableName).append(" 表结构变更\n\n");
        sb.append("变更内容：\n");

        if (changes != null) {
            for (String change : changes) {
                sb.append("- ").append(change).append("\n");
            }
        }

        sb.append("\n🤖 Generated by DBAgent");
        return sb.toString();
    }

    /**
     * 使用前端配置判断是否需要认证
     */
    private boolean isAuthRequired(DBAgentConfig config) {
        String username = config.getGitUsername() != null ? config.getGitUsername() : defaultUsername;
        String token = config.getGitToken() != null ? config.getGitToken() : defaultToken;
        return username != null && !username.isBlank() &&
                token != null && !token.isBlank();
    }

    /**
     * 使用默认配置判断是否需要认证
     */
    private boolean isAuthRequired() {
        return defaultUsername != null && !defaultUsername.isBlank() &&
                defaultToken != null && !defaultToken.isBlank();
    }

    /**
     * 使用前端配置获取认证提供者，无凭据时返回null
     */
    private CredentialsProvider getCredentialsProvider(DBAgentConfig config) {
        String username = config.getGitUsername() != null ? config.getGitUsername() : defaultUsername;
        String token = config.getGitToken() != null ? config.getGitToken() : defaultToken;
        log.debug("Git凭据解析: configUser={}, configToken={}, resolvedUser={}, resolvedToken={}",
                config.getGitUsername(), config.getGitToken() != null ? "***" : "null",
                username, token != null ? "***" : "null");
        if (username == null || username.isBlank() || token == null || token.isBlank()) {
            log.warn("Git凭据为空，跳过认证: username={}, token={}", username, token != null ? "***" : "null");
            return null;
        }
        return new UsernamePasswordCredentialsProvider(username, token);
    }

    /**
     * 使用默认配置获取认证提供者，无凭据时返回null
     */
    private CredentialsProvider getCredentialsProvider() {
        if (defaultUsername == null || defaultUsername.isBlank() || defaultToken == null || defaultToken.isBlank()) {
            return null;
        }
        return new UsernamePasswordCredentialsProvider(defaultUsername, defaultToken);
    }

    /**
     * 测试Git连接
     */
    public boolean testConnection(DBAgentConfig config) {
        String repoUrl = config.getGitRepoUrl() != null ? config.getGitRepoUrl() : defaultRepoUrl;
        if (repoUrl == null || repoUrl.isBlank()) {
            return false;
        }

        try {
            Path gitPath = Paths.get(localPath);
            File gitDir = gitPath.resolve(".git").toFile();

            if (gitDir.exists()) {
                try (Git git = Git.open(gitPath.toFile())) {
                    var fetchCmd = git.fetch();
                    CredentialsProvider fetchCp = getCredentialsProvider(config);
                    if (fetchCp != null) {
                        fetchCmd.setCredentialsProvider(fetchCp);
                    }
                    fetchCmd.call();
                    return true;
                }
            }

            // 尝试克隆到临时目录
            Path tempDir = Files.createTempDirectory("dbagent-git-test");
            try {
                var cloneCommand = Git.cloneRepository()
                        .setURI(repoUrl)
                        .setDirectory(tempDir.toFile())
                        .setNoCheckout(true);

                CredentialsProvider cloneCp = getCredentialsProvider(config);
                if (cloneCp != null) {
                    cloneCommand.setCredentialsProvider(cloneCp);
                }

                try (Git git = cloneCommand.call()) {
                    return true;
                }
            } finally {
                // 清理临时目录
                deleteDirectory(tempDir.toFile());
            }
        } catch (Exception e) {
            log.error("Git连接测试失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 测试默认配置Git连接
     */
    public boolean testConnection() {
        if (defaultRepoUrl == null || defaultRepoUrl.isBlank()) {
            return false;
        }

        try {
            Path gitPath = Paths.get(localPath);
            File gitDir = gitPath.resolve(".git").toFile();

            if (gitDir.exists()) {
                try (Git git = Git.open(gitPath.toFile())) {
                    if (isAuthRequired()) {
                        git.fetch().setCredentialsProvider(getCredentialsProvider()).call();
                    } else {
                        git.fetch().call();
                    }
                    return true;
                }
            }

            // 尝试克隆到临时目录
            Path tempDir = Files.createTempDirectory("dbagent-git-test");
            try {
                var cloneCommand = Git.cloneRepository()
                        .setURI(defaultRepoUrl)
                        .setDirectory(tempDir.toFile())
                        .setNoCheckout(true);

                CredentialsProvider defCloneCp = getCredentialsProvider();
                if (defCloneCp != null) {
                    cloneCommand.setCredentialsProvider(defCloneCp);
                }

                try (Git git = cloneCommand.call()) {
                    return true;
                }
            } finally {
                // 清理临时目录
                deleteDirectory(tempDir.toFile());
            }
        } catch (Exception e) {
            log.error("Git连接测试失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 获取远程分支列表
     */
    public List<String> listRemoteBranches(DBAgentConfig config) {
        String repoUrl = config.getGitRepoUrl() != null ? config.getGitRepoUrl() : defaultRepoUrl;
        if (repoUrl == null || repoUrl.isBlank()) {
            throw new RuntimeException("Git仓库未配置");
        }

        try {
            var lsRemoteCmd = Git.lsRemoteRepository()
                    .setHeads(true)
                    .setRemote(repoUrl);

            CredentialsProvider lsCp = getCredentialsProvider(config);
            if (lsCp != null) {
                lsRemoteCmd.setCredentialsProvider(lsCp);
            }

            Collection<Ref> refs = lsRemoteCmd.call();
            return refs.stream()
                    .map(ref -> {
                        String name = ref.getName();
                        // refs/heads/feature/ai-dao -> feature/ai-dao
                        return name.startsWith("refs/heads/") ? name.substring("refs/heads/".length()) : name;
                    })
                    .sorted()
                    .toList();
        } catch (Exception e) {
            log.error("获取远程分支列表失败: {}", e.getMessage());
            throw new RuntimeException("获取远程分支列表失败: " + e.getMessage(), e);
        }
    }

    /**
     * 递归删除目录
     */
    private void deleteDirectory(File directory) {
        if (directory.isDirectory()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    deleteDirectory(file);
                }
            }
        }
        directory.delete();
    }
}
