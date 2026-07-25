package org.example.agent.client;


import org.example.agent.PropsConfig;
import org.springframework.beans.factory.FactoryBean;

import java.lang.reflect.Proxy;


public class UfanClientFactoryBean<T> implements FactoryBean<T> {

    private final Class<T> type;
    private PropsConfig propsConfig;

    public UfanClientFactoryBean(Class<T> type) {
        this.type = type;
    }


    @Override
    public T getObject() {
        return (T) Proxy.newProxyInstance(
                type.getClassLoader(),
                new Class[]{type},
                new UfanClientProxy(propsConfig)
        );
    }

    @Override
    public Class<?> getObjectType() {
        return type;
    }

    public void setPropsConfig(PropsConfig propsConfig) { this.propsConfig = propsConfig; }

}