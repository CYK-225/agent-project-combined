package org.example.agent;


import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
@Data
public class PropsConfig {

    @Value("${takeout.appSecret}")
    private String appId;


    @Value("${takeout.appSecret}")
    private String appSecret;


    @Value("${takeout.baseUrl}")
    private String baseUrl;
}
