package com.example.basin.evacuation;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
@OpenAPIDefinition(
        info = @Info(
                title = "Basin Evacuation Decision Service",
                version = "1.0.0",
                description = "Watershed evacuation decision support for Sichuan Basin / western Chongqing. "
                        + "Manages administrative districts, immutable risk snapshots, four-level decisions "
                        + "with evidence-version traceability, manual overrides and a transactional notification outbox.",
                contact = @Contact(name = "Basin Evacuation Platform")
        )
)
public class BasinEvacuationApplication {

    public static void main(String[] args) {
        SpringApplication.run(BasinEvacuationApplication.class, args);
    }
}
