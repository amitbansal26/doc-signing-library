package com.docsigning;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EnableJpaRepositories(basePackages = "com.docsigning.repository")
public class DocSigningApplication {

    public static void main(String[] args) {
        SpringApplication.run(DocSigningApplication.class, args);
    }
}
