package com.zr.praxisai.aop;

import cn.dev33.satoken.stp.StpUtil;
import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.annotation.NacosInjected;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.exception.NacosException;
import com.zr.praxisai.annotation.LimitCheck;
import com.zr.praxisai.common.ErrorCode;
import com.zr.praxisai.exception.BusinessException;
import com.zr.praxisai.manager.CounterManager;
import com.zr.praxisai.model.entity.User;
import com.zr.praxisai.service.UserService;
import com.zr.praxisai.utils.NetUtils;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
@Aspect
@Component
public class LimitAspect {

    @Resource
    private UserService userService;

    @Before("@annotation(limitCheck)")  //拦截所有带limitCheck注解的方法
    public void beforeMethod(LimitCheck limitCheck){
        //获取当前请求上下文
        RequestAttributes requestAttributes = RequestContextHolder.currentRequestAttributes();
        //获取Http请求
        HttpServletRequest request = ((ServletRequestAttributes) requestAttributes).getRequest();
        // 显式获取当前登录用户
        User loginUser = userService.getLoginUser(request);
        if (loginUser == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        //获取客户端IP地址
        String ip = NetUtils.getIpAddress(request);
        //校验是否是爬虫
        crawlerDetect(loginUser.getId(), ip);
    }

    @Resource
    private CounterManager counterManager;
    //检测loginUserId的爬虫
    private void crawlerDetect(long loginUserId, String ip) {
        // 调用多少次时告警
        final int WARN_COUNT = 10;
        // 调用多少次时封号
        final int BAN_COUNT = 20;
        // 拼接访问 key
        String key = String.format("user:access:%s", loginUserId);
        // 统计一分钟内访问次数，180 秒过期
        long count = counterManager.incrAndGetCounter(key, 1, TimeUnit.MINUTES, 180);
        // 是否封号
        if (count > BAN_COUNT) {
            //踢下线
            StpUtil.kickout(loginUserId);
            //更新 ip 黑名单
            updateNacosBlackList(ip);
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "访问太频繁，已被封号");
        }
        // 是否告警
        if (count == WARN_COUNT) {
            // 可以改为向管理员发送邮件通知
            throw new BusinessException(110, "警告访问太频繁");
        }
    }

    @Value("${nacos.config.data-id}")
    private String dataId;
    @Value("${nacos.config.group}")
    private String group;
    @NacosInjected
    private ConfigService configService;  //与 nacos 配置中心交互

    private void updateNacosBlackList(String ip){
        try {
            String content = configService.getConfig(dataId, group, 5000);
            // 解析 yaml 文件
            Yaml yaml = new Yaml();
            //将Nacos配置文件解析为Map
            Map<String, Object> yamlMap = yaml.load(content);
            //获取黑名单列表
            List<String> blacklist = (List<String>) yamlMap.get("blackIpList");
            // 如果黑名单为空，初始化一个空列表
            if (blacklist == null) {
                blacklist = new ArrayList<>();
            }
            //将该IP添加到黑名单列表
            if (!blacklist.contains(ip)) {
                blacklist.add(ip);
            }
            // 更新 YAML 中的黑名单列表
            yamlMap.put("blackIpList", blacklist);
            // 使用 SnakeYAML 的 DumperOptions 设置输出样式为 BLOCK
            DumperOptions options = new DumperOptions();
            options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK); // 设置为块格式

            Yaml blockYaml = new Yaml(options);
            // 转换为 YAML 格式字符串
            String updatedContent = blockYaml.dump(yamlMap);

            //发布到nacos
            boolean isPublishOk = configService.publishConfig(dataId, group, updatedContent);
            if (isPublishOk) {
                System.out.println("配置更新成功");
            } else {
                System.out.println("配置更新失败");
            }
        } catch (NacosException e) {
            e.printStackTrace();
        }
    }
}