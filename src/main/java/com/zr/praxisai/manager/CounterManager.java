package com.zr.praxisai.manager;
import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.IntegerCodec;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.Instant;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * 基于原子类，通用计数器（可用于实现频率统计、限流、封禁等等）
 */
@Slf4j
@Service
public class CounterManager {

    @Resource
    private RedissonClient redissonClient;

    /// 三个重载的计数方法
    //原子计数器（缓存键，时间间隔，时间间隔单位，计数器缓存过期时间）
    public long incrAndGetCounter(String key, int timeInterval, TimeUnit timeUnit,
                                  long expirationTimeInSeconds) {
        if (StrUtil.isBlank(key)) {
            return 0;
        }
        long timeFactor;  //时间片
 // （比如时间间隔1min,当前时间7:05，基准时间7:00，则当前时间片为5，即第五个时间间隔内，所有在这个时间片内的访问次数同+）
        switch (timeUnit) {
            case SECONDS:
                //获取1970年1.1到当前过的秒数(时间戳) // 时间间隔
                //表示当前时间所在的时间片位置（比如）
                timeFactor = Instant.now().getEpochSecond() / timeInterval;
                break;
            case MINUTES:
                timeFactor = Instant.now().getEpochSecond() / timeInterval / 60;
                break;
            case HOURS:
                timeFactor = Instant.now().getEpochSecond() / timeInterval / 3600;
                break;
            default:
                throw new IllegalArgumentException("不支持的单位");
        }
        //动态生成 Redis Key
        String redisKey = key + ":" + timeFactor;

        // Lua 脚本
        String luaScript =
                // 判断键是否存在，存在则自增，不存在则设置值并设置过期时间，并返回自增后的值
                "if redis.call('exists', KEYS[1]) == 1 then " +
                        "  return redis.call('incr', KEYS[1]); " +
                        "else " +
                        "  redis.call('set', KEYS[1], 1); " +
                        "  redis.call('expire', KEYS[1], ARGV[1]); " +
                        "  return 1; " +
                        "end";

        // --执行 Lua 脚本--
        //获取脚本执行器
        RScript script = redissonClient.getScript(IntegerCodec.INSTANCE);
       // 执行脚本并返回结果
        Object countObj = script.eval(
                RScript.Mode.READ_WRITE,  // 表示脚本需要读写权限
                luaScript,
                RScript.ReturnType.INTEGER, //指定返回值类型为整数
                //传递给脚本的Key列表，这里是用户key
                Collections.singletonList(redisKey),
                expirationTimeInSeconds  //传递给脚本的参数列表（这里是过期时间）
        );
        return (long) countObj;
    }



    //增加并返回计数 （缓存键，时间间隔，时间间隔单位）
    public long incrAndGetCounter(String key, int timeInterval, TimeUnit timeUnit) {
        int expirationTimeInSeconds; //过期时间
        switch (timeUnit) {  //根据对应时间单位，把时间间隔转成过期时间，单位秒
            case SECONDS:
                expirationTimeInSeconds = timeInterval;
                break;
            case MINUTES:
                expirationTimeInSeconds = timeInterval * 60;
                break;
            case HOURS:
                expirationTimeInSeconds = timeInterval * 60 * 60;
                break;
            default:
                throw new IllegalArgumentException("不支持的时间单位，请使用 SECONDS, MINUTES, or HOURS.");
        }
        //调用法3
        return incrAndGetCounter(key, timeInterval, timeUnit, expirationTimeInSeconds);
    }

    //原子计数器，默认统计用户key一分钟(固定窗口)内的计数结果
    public long incrAndGetCounter(String key) {
        //调用法2
        return incrAndGetCounter(key, 1, TimeUnit.MINUTES);
    }
}














