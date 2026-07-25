package org.example.common.easyExcel.config;

import org.example.common.easyExcel.handler.ExcelImportResolver;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * EasyExcel 自动配置。
 * 注册 {@link ExcelImportResolver} 到 Spring MVC 参数解析链。
 * <p>
 * {@link org.example.common.easyExcel.handler.ExcelExportAdvice} 由 {@code @ControllerAdvice} 自动扫描，无需额外注册。
 *
 * @author yfyuanke
 * @since 2026-06-02
 */
@Configuration
public class EasyExcelAutoConfiguration implements WebMvcConfigurer {

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(0, new ExcelImportResolver());
    }
}
