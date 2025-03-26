package com.zr.praxisai.blackfilter;

import cn.hutool.bloomfilter.BitMapBloomFilter;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.yaml.snakeyaml.Yaml;

import java.util.List;
import java.util.Map;

/**
 * 黑名单过滤工具类
 */
@Slf4j
public class BlackIpUtils {
    //静态的布隆过滤器,存IP黑名单,默认大小100
    private static BitMapBloomFilter bloomFilter;

    // 判断 ip 是否在黑名单里
    public static boolean isBlackIp(String ip) {
        //true 表示可能在黑名单里
        //false 一定不在黑名单里
        return bloomFilter.contains(ip);
    }

    //获取 nacos 上的黑名单,添加到布隆过滤器中
    public static void rebuildBlackIp(String configInfo) {
        //判断配置信息是否为空
        if (StrUtil.isBlank(configInfo)) {
            configInfo = "{}";
        }
        // 解析 yaml 文件
        Yaml yaml = new Yaml();
        //将传入的配置信息解析成 Map 对象
        Map map = yaml.loadAs(configInfo, Map.class);

        // 获取配置中的 IP 黑名单
        List<String> blackIpList = (List<String>) map.get("blackIpList");

        // 加锁防止多线程修改布隆过滤器
        synchronized (BlackIpUtils.class) {
            //黑名单不为空
            if (CollUtil.isNotEmpty(blackIpList)) {
                // 注意构造参数的设置，容量越大，误判率越低
                BitMapBloomFilter bitMapBloomFilter = new BitMapBloomFilter(1000);
                //遍历黑名单,将黑名单添加到布隆过滤器中
                for (String blackIp : blackIpList) {
                    bitMapBloomFilter.add(blackIp);
                }
                bloomFilter = bitMapBloomFilter;
            }
            //黑名单为空
            else {
                bloomFilter = new BitMapBloomFilter(1000);
            }
        }
    }
}
