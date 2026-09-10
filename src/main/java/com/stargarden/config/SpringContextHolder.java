package com.stargarden.config;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

/**
 * Spring 上下文持有器：
 * WebSocket 端点（@ServerEndpoint）由容器直接实例化、不归 Spring 管理，
 * 无法使用 @Autowired，需要通过本工具静态获取 Spring Bean。
 */
@Component
public class SpringContextHolder implements ApplicationContextAware {

    private static ApplicationContext context;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        context = applicationContext;
    }

    /** 按 Bean 类型获取 Spring 容器中的实例 */
    public static <T> T getBean(Class<T> clazz) {
        return context.getBean(clazz);
    }
}
