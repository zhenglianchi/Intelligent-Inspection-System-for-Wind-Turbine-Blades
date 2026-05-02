package com.itheima.consultant.handler;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

/**
 * Spring Bean获取工具
 * 用于解决在非Spring管理的Listener中获取Bean的问题
 *
 * @Author MH.Zhang
 * @Description 处理new Listener的cache注入问题
 * @Migration migrated from wtb-health-monitor
 */
@Component
public class GetBeanHandler implements ApplicationContextAware {
    private static ApplicationContext applicationContext = null;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        if (GetBeanHandler.applicationContext == null) {
            GetBeanHandler.applicationContext = applicationContext;
        }
    }

    /**
     * 获取ApplicationContext
     *
     * @return ApplicationContext
     */
    public static ApplicationContext getApplicationContext() {
        return applicationContext;
    }

    /**
     * 根据bean名称获取Bean
     *
     * @param beanName Bean名称
     * @return Bean实例
     */
    public static Object getBean(String beanName) {
        return applicationContext.getBean(beanName);
    }

    /**
     * 根据类型获取Bean
     *
     * @param c   Bean类型
     * @param <T> 泛型
     * @return Bean实例
     */
    public static <T> T getBean(Class<T> c) {
        return applicationContext.getBean(c);
    }

    /**
     * 根据名称和类型获取Bean
     *
     * @param name Bean名称
     * @param c    Bean类型
     * @param <T>  泛型
     * @return Bean实例
     */
    public static <T> T getBean(String name, Class<T> c) {
        return getApplicationContext().getBean(name, c);
    }
}
