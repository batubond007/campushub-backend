package com.campushub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class CampushubBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(CampushubBackendApplication.class, args);
	}

}
