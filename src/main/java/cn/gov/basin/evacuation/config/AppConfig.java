package cn.gov.basin.evacuation.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class AppConfig {

    @Bean
    public Clock systemClock() {
        return Clock.systemUTC();
    }

    @Bean
    public OpenAPI basinOpenAPI() {
        return new OpenAPI().info(new Info()
                .title("Basin Evacuation Decision API")
                .description("四川盆地与重庆西部流域转移决策服务")
                .version("1.0.0"));
    }
}
