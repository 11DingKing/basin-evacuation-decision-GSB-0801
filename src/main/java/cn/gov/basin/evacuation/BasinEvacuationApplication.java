package cn.gov.basin.evacuation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class BasinEvacuationApplication {

    public static void main(String[] args) {
        SpringApplication.run(BasinEvacuationApplication.class, args);
    }
}
