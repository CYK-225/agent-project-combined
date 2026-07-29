package com.cyk.DockerTool;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.transport.DockerHttpClient;
import com.github.mwiede.dockerjava.jsch.JschDockerHttpClient;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.UserInfo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.time.Duration;

@Configuration
public class DockerConfiguration {

    @Value("${docker.host}")
    private String dockerHost;

    @Value("${docker.password}")
    private String dockerPassword;

    @Bean
    public DockerClient dockerClient() throws JSchException, IOException {
        DefaultDockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withDockerHost(dockerHost)
                // 如果你的 2376 开启了 TLS 证书验证，取消下方注释并配置证书路径
                // .withDockerTlsVerify(true)
                // .withDockerCertPath("/path/to/certs")
                .build();

//        DockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
//                .dockerHost(config.getDockerHost())
//                .sslConfig(config.getSSLConfig())
//                .maxConnections(100)
//                .connectionTimeout(Duration.ofSeconds(30))
//                .responseTimeout(Duration.ofSeconds(45))
//                .build();
        DockerHttpClient httpClient = new JschDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                // ======== 超时配置 ========
                .connectTimeout(Duration.ofSeconds(30))
                .readTimeout(Duration.ofSeconds(45))
                // ======== 注入 SSH 密码认证 ========
                .userInfo(new UserInfo() {
                    @Override
                    public String getPassphrase() {
                        return null; // 如果你不用私钥的密码短语，返回 null 即可
                    }

                    @Override
                    public String getPassword() {
                        return dockerPassword;
                    }

                    @Override
                    public boolean promptPassword(String message) {
                        return true; // 告诉底层：我准备好密码了，请直接调 getPassword() 拿
                    }

                    @Override
                    public boolean promptPassphrase(String message) {
                        return false;
                    }

                    @Override
                    public boolean promptYesNo(String message) {
                        return true; // 对于各种确认提示默认选 Yes
                    }

                    @Override
                    public void showMessage(String message) {
                        // 可以留空，或者加个 log 打印 JSch 返回的信息
                    }
                })
                // =======================================
                .build();

        return DockerClientImpl.getInstance(config, httpClient);
    }
}