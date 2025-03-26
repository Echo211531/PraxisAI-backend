package com.zr.praxisai.sentinel;

import cn.hutool.core.io.FileUtil;
import com.alibaba.csp.sentinel.datasource.*;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRuleManager;
import com.alibaba.csp.sentinel.slots.block.degrade.circuitbreaker.CircuitBreakerStrategy;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowRuleManager;
import com.alibaba.csp.sentinel.transport.util.WritableDataSourceRegistry;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Sentinel 限流熔断规则管理器
 *
 */
@Component
public class SentinelRulesManager {

    @PostConstruct
    public void initRules() throws Exception {
        initFlowRules();   //初始化限流规则
        initDegradeRules(); //初始化降级规则
        listenRules();  //持久化配置为本地文件
    }
    //初始化默认的限流规则
    public void initFlowRules() {
        // 单 IP 查看题目列表限流规则
        ParamFlowRule rule = new ParamFlowRule(SentinelConstant.listQuestionVOByPage)
                .setParamIdx(0) // 对第 0 个参数限流，即 IP 地址
                .setCount(60) // 每分钟最多 60 次
                .setDurationInSec(60);  // 规则的统计周期为 60 秒
        ParamFlowRuleManager.loadRules(Collections.singletonList(rule)); //应用规则
    }

    //初始化默认的降级规则
    public void initDegradeRules() {
        // 单 IP 查看题目列表熔断规则
        DegradeRule slowCallRule = new DegradeRule(SentinelConstant.listQuestionVOByPage)
                .setGrade(CircuitBreakerStrategy.SLOW_REQUEST_RATIO.getType())
                .setCount(0.2) // 慢调用比例大于 20%
                .setTimeWindow(60) // 熔断持续时间 60 秒
                .setStatIntervalMs(30 * 1000) // 统计时长 30 秒
                .setMinRequestAmount(10) // 最小请求数
                .setSlowRatioThreshold(3); // 响应时间超过 3 秒

        DegradeRule errorRateRule = new DegradeRule(SentinelConstant.listQuestionVOByPage)
                .setGrade(CircuitBreakerStrategy.ERROR_RATIO.getType())
                .setCount(0.1) // 异常率大于 10%
                .setTimeWindow(60) // 熔断持续时间 60 秒
                .setStatIntervalMs(30 * 1000) // 统计时长 30 秒
                .setMinRequestAmount(10); // 最小请求数

        // 加载规则
        DegradeRuleManager.loadRules(Arrays.asList(slowCallRule, errorRateRule));
    }

    //持久化配置为本地文件
    public void listenRules() throws Exception {
        // 获取当前项目根目录
        String rootPath = System.getProperty("user.dir");
        // 在当前项目路径下 创建 sentinel 目录路径
        File sentinelDir = new File(rootPath, "sentinel");
        // 目录不存在则创建
        if (!FileUtil.exist(sentinelDir)) {
            FileUtil.mkdir(sentinelDir);
        }
        // 规则文件路径，分别存限流和降级规则
        String flowRulePath = new File(sentinelDir, "FlowRule.json").getAbsolutePath();
        String degradeRulePath = new File(sentinelDir, "DegradeRule.json").getAbsolutePath();

        //采用Sentinel 提供的拉模式数据源，定期从文件读取规则（默认每 2 秒拉取一次）
        ReadableDataSource<String, List<FlowRule>> flowRuleDataSource =
                new FileRefreshableDataSource<>(flowRulePath, flowRuleListParser); //反序列化
        //将规则注册到内存中
        FlowRuleManager.register2Property(flowRuleDataSource.getProperty());
        //将内存中的规则写入文件，系统会自动确保规则变更后持久化到文件中
        WritableDataSource<List<FlowRule>> flowWds =
                new FileWritableDataSource<>(flowRulePath, this::encodeJson); //序列化为json
        // 注册写数据源，当规则通过 API 更新时，自动写入文件
        WritableDataSourceRegistry.registerFlowDataSource(flowWds);

        //熔断持久化配置，和上面限流类似
        FileRefreshableDataSource<List<DegradeRule>> degradeRuleDataSource
                = new FileRefreshableDataSource<>(
                degradeRulePath, degradeRuleListParser);
        DegradeRuleManager.register2Property(degradeRuleDataSource.getProperty());
        WritableDataSource<List<DegradeRule>> degradeWds =
                new FileWritableDataSource<>(degradeRulePath, this::encodeJson);

        WritableDataSourceRegistry.registerDegradeDataSource(degradeWds);
    }
    //将 JSON 字符串解析为 List<FlowRule>
    private Converter<String, List<FlowRule>> flowRuleListParser = source -> JSON.parseObject(source,
            new TypeReference<List<FlowRule>>() {
            });
    private Converter<String, List<DegradeRule>> degradeRuleListParser = source -> JSON.parseObject(source,
            new TypeReference<List<DegradeRule>>() {
            });

    private <T> String encodeJson(T t) {
        return JSON.toJSONString(t);
    }
}