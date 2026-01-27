package com.moa.moa_backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class MoaBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(MoaBackendApplication.class, args);
	}

}
