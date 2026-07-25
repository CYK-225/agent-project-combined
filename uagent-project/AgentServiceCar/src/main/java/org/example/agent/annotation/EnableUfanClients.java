package org.example.agent.annotation;

import org.example.agent.client.UfanClientRegistrar;
import org.springframework.context.annotation.Import;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Import(UfanClientRegistrar.class)
public @interface EnableUfanClients {
    String[] basePackages();
}