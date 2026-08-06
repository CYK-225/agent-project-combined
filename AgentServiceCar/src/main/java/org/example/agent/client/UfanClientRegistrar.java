package org.example.agent.client;


import lombok.extern.slf4j.Slf4j;
import org.example.agent.annotation.EnableUfanClients;
import org.example.agent.annotation.UfanClientService;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.filter.AnnotationTypeFilter;

import java.beans.Introspector;
import java.util.Map;
import java.util.Set;

@Slf4j
public class UfanClientRegistrar implements ImportBeanDefinitionRegistrar {

    @Override
    public void registerBeanDefinitions(
            AnnotationMetadata metadata,
            BeanDefinitionRegistry registry) {

        Map<String, Object> attrs =
                metadata.getAnnotationAttributes(EnableUfanClients.class.getName());

        String[] basePackages = (String[]) attrs.get("basePackages");

        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false) {
                    @Override
                    protected boolean isCandidateComponent(AnnotatedBeanDefinition beanDefinition) {
                        return beanDefinition.getMetadata().isInterface()
                                && beanDefinition.getMetadata().isIndependent();
                    }
                };

        // 扫描带注解的接口
        scanner.addIncludeFilter(new AnnotationTypeFilter(UfanClientService.class));

        for (String basePackage : basePackages) {
            Set<BeanDefinition> candidates = scanner.findCandidateComponents(basePackage);
            log.info("加载悠饭客户端接口,package={}", basePackage);
            for (BeanDefinition candidate : candidates) {
                String className = candidate.getBeanClassName();
                log.info("注册悠饭客户端接口 {}", className);
                Class<?> clazz = null;
                try {
                    clazz = Class.forName(className);
                } catch (ClassNotFoundException e) {
                    throw new RuntimeException(e);
                }

                BeanDefinitionBuilder builder =
                        BeanDefinitionBuilder.genericBeanDefinition(UfanClientFactoryBean.class);
                builder.getRawBeanDefinition().setAttribute(
                        FactoryBean.OBJECT_TYPE_ATTRIBUTE, clazz
                );

                builder.addConstructorArgValue(clazz);
                builder.addPropertyReference("propsConfig", "propsConfig");

                // 设置依赖顺序，保证这些 Bean 先创建
                builder.getRawBeanDefinition().setDependsOn( "propsConfig");
                String beanName = Introspector.decapitalize(clazz.getSimpleName());
                registry.registerBeanDefinition(beanName, builder.getBeanDefinition());
            }
        }
    }
}