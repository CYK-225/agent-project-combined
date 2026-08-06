package org.example.common.commonUtils;

import org.jetbrains.annotations.NotNull;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

@Component
public class SpringContextUtil implements ApplicationContextAware {
    private static ApplicationContext context;

    @Override
    public void setApplicationContext(@NotNull ApplicationContext applicationContext) {
        context = applicationContext;
    }

    public static String getProperty(String key) {
        return context.getEnvironment().getProperty(key);
    }
    public static String getProperty(String key, String defaultValue) {
        return context.getEnvironment().getProperty(key, defaultValue);
    }
}
