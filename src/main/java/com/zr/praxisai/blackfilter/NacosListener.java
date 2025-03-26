package com.zr.praxisai.blackfilter;
import com.alibaba.nacos.api.annotation.NacosInjected;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.Listener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.validation.constraints.NotNull;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Nacos 监听器
 */
@Slf4j
@Component
//表示该类会在 Spring 容器初始化完成后自动调用 afterPropertiesSet 方法
public class NacosListener implements InitializingBean {

    @NacosInjected
    private ConfigService configService;  //与 nacos 配置中心交互
    @Value("${nacos.config.data-id}")
    private String dataId;
    @Value("${nacos.config.group}")
    private String group;

    @Override
    public void afterPropertiesSet() throws Exception {
        log.info("nacos 监听器启动");
        //从 Nacos 获取指定 dataId 和 group 的配置内容
        String config = configService.getConfigAndSignListener(dataId, group, 3000L,
                new Listener() { //注册一个nacos监听器，监听配置信息的变化

            //定义线程工程
            final ThreadFactory threadFactory = new ThreadFactory() {
                //原子类，用于生成线程名称
                private final AtomicInteger poolNumber = new AtomicInteger(1);
                @Override
                public Thread newThread(@NotNull Runnable r) {
                    Thread thread = new Thread(r);
                    thread.setName("refresh-ThreadPool" + poolNumber.getAndIncrement());
                    return thread;
                }
            };
            //自定义线程池：使用固定大小的线程池，传入线程工厂,用于异步处理配置变化的逻辑
            final ExecutorService executorService = Executors.newFixedThreadPool(1, threadFactory);

            // 通过线程池异步处理黑名单变化的逻辑
            @Override
            public Executor getExecutor() {
                return executorService;
            }

            // 监听后续黑名单变化
           //当nacos配置中心配置信息发生变化时，会接收新的配置configInfo加载到布隆过滤器中
            @Override
            public void receiveConfigInfo(String configInfo) {
                log.info("监听到配置信息变化：{}", configInfo);
                //将新的黑名单配置加载到布隆过滤器中
                BlackIpUtils.rebuildBlackIp(configInfo);
            }
        });

        // 初始化黑名单
        BlackIpUtils.rebuildBlackIp(config);
    }
}