package com.basin.evacuation.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI evacuationOpenAPI() {
        return new OpenAPI().info(new Info()
                .title("流域转移决策服务")
                .version("1.0.0")
                .description("面向四川盆地与重庆西部基层值班人员的转移决策服务：行政区、不可变风险快照、"
                        + "四级决策建议、人工覆写（含操作者/理由/过期时间）与通知 outbox。"
                        + "数据不足（INSUFFICIENT_DATA）与低风险（LOW_RISK）是两种完全不同的结果。")
                .contact(new Contact().name("basin-evacuation")));
    }
}
