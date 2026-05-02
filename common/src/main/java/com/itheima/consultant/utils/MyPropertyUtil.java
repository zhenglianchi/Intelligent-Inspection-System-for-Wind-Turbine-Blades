package com.itheima.consultant.utils;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.env.ConfigurablePropertyResolver;
import org.springframework.core.env.PropertyResolver;
import org.springframework.stereotype.Component;

/**
 * 属性配置工具
 * 允许在代码中静态获取application.properties配置属性
 *
 * @Migration migrated from wtb-health-monitor
 */
@Component
public class MyPropertyUtil extends PropertySourcesPlaceholderConfigurer {

    private static PropertyResolver propertyResolver;

    @Override
    protected void processProperties(ConfigurableListableBeanFactory beanFactoryToProcess,
                                     ConfigurablePropertyResolver propertyResolver) throws BeansException {
        super.processProperties(beanFactoryToProcess, propertyResolver);
        MyPropertyUtil.propertyResolver = propertyResolver;
    }

    /**
     * 获取配置属性字符串
     *
     * @param key 属性key
     * @return 属性值
     */
    public static String getString(String key) {
        return propertyResolver.getProperty(key);
    }
}
