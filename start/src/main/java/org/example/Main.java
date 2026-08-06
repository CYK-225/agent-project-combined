package org.example;



import org.example.agent.annotation.EnableUfanClients;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@EnableUfanClients(basePackages = "org.example.agent.client.service")
@SpringBootApplication(scanBasePackages = {"org.example"})
public class Main {
    public static void main(String[] args) {
        // 这一行才是真正启动 Spring Boot 容器、加载配置和内嵌 Web 服务器的核心代码！
        SpringApplication.run(Main.class, args);

        System.out.println("====== Spring Boot 服务启动成功 ======");

    }
}