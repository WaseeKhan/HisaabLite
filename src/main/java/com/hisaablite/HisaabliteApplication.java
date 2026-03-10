package com.hisaablite;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EntityScan(basePackages = "com.hisaablite.entity")
@EnableJpaRepositories(basePackages = {
		"com.hisaablite.repository",
		"com.hisaablite.admin.repository"
})
public class HisaabliteApplication {
	public static void main(String[] args) {
		SpringApplication.run(HisaabliteApplication.class, args);
	}

}
