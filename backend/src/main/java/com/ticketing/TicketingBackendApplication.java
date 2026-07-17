package com.ticketing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class TicketingBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(TicketingBackendApplication.class, args);
	}

}
