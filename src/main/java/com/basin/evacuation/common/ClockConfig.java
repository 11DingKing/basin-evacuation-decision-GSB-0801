package com.basin.evacuation.common;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 可注入时钟：生产环境使用系统时钟（Asia/Shanghai），
 * 测试中替换为可设置的 Clock 以覆盖覆写过期的边界时刻。
 */
@Configuration
public class ClockConfig {

    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    @Bean
    public Clock clock() {
        return Clock.system(ZONE);
    }
}
