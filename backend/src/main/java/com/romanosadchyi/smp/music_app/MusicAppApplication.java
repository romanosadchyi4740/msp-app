package com.romanosadchyi.smp.music_app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class MusicAppApplication {

	static void main(String[] args) {
		SpringApplication.run(MusicAppApplication.class, args);
	}
}
