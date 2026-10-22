package com.afrismart;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.modulith.Modulithic;

@Modulithic(systemName = "AfriSmart", sharedModules = "shared")
@SpringBootApplication
@EnableJpaAuditing
@ConfigurationPropertiesScan
public class AfriSmartApplication {

    public static void main(String[] args) {
        SpringApplication.run(AfriSmartApplication.class, args);
    }
}
