package com.cyk;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableAsync
@EnableScheduling
@SpringBootApplication(scanBasePackages = {"com.cyk"})

@MapperScan({
        "com.cyk.Mapper",
        "com.cyk.task.DAL.Mapper",
        "com.cyk.acl.agent.mapper"
})
public class BaiduApplication {

    public static void main(String[] args) {
        SpringApplication.run(BaiduApplication.class, args);
    }

}
