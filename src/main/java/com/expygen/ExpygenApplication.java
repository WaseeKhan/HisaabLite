package com.expygen;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EntityScan(basePackages = "com.expygen.entity")
@EnableJpaRepositories(basePackages = {
		"com.expygen.repository",
		"com.expygen.admin.repository"
})

@EnableScheduling 
public class ExpygenApplication {

	public static void main(String[] args) {
		SpringApplication.run(ExpygenApplication.class, args);
	}

}
