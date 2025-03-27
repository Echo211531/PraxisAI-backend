package com.zr.praxisai.config;

import com.volcengine.ark.runtime.service.ArkService;
import lombok.Data;
import okhttp3.ConnectionPool;
import okhttp3.Dispatcher;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
@ConfigurationProperties(prefix = "ai")
@Data
public class AiConfig {

    // ApiKey
    private String apiKey;

    //AI 请求客户端
    @Bean
    public ArkService aiService() {
        //创建一个连接池对象，用于管理 HTTP 连接
        ConnectionPool connectionPool = new ConnectionPool(5, 1, TimeUnit.SECONDS);
        //创建一个调度器对象，用于处理请求队列
        Dispatcher dispatcher = new Dispatcher();
        //根据apikey调用DeepSeek大模型接口
        ArkService service = ArkService.builder().dispatcher(dispatcher).connectionPool(connectionPool)
                .baseUrl("https://ark.cn-beijing.volces.com/api/v3")
                .apiKey(apiKey)
                .build();
        //客户端初始化
        return service;
    }
}
