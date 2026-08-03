package gov.basin.evac.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI basinEvacuationOpenApi() {
        return new OpenAPI().info(new Info()
                .title("流域转移决策服务 API")
                .version("1.0.0")
                .description("四川盆地/重庆西部流域转移决策服务：行政区、不可变风险快照、四级决策建议、"
                        + "人工覆写与通知 outbox。人口单位固定为“人”，每条建议均标注引用的证据版本。"));
    }
}
