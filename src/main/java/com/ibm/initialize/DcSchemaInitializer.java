package com.ibm.initialize;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;

@SpringBootApplication(scanBasePackages= "com.ibm*")
public class DcSchemaInitializer extends SpringBootServletInitializer{

	@Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder builder) {
        return builder.sources(DcSchemaInitializer.class);
    }
	
	public static void main(String[] args) {
		SpringApplication.run(DcSchemaInitializer.class, args);

	}

}
