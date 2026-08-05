package com.reForm.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableCaching
@EnableAsync
public class BackendApplication {

	public static void main(String[] args) {
		System.out.println("Hello world!");
		SpringApplication.run(BackendApplication.class, args);
	}

}


