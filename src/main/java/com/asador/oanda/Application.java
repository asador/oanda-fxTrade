package com.asador.oanda;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.retry.backoff.FixedBackOffPolicy;
import org.springframework.retry.policy.AlwaysRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

@SpringBootApplication
public class Application {

	@Bean
	public RetryTemplate retryTemplate() {
		RetryTemplate retryTemplate = new RetryTemplate();
		retryTemplate.setRetryPolicy(new AlwaysRetryPolicy());
		FixedBackOffPolicy backOffPolicy = new FixedBackOffPolicy();
		backOffPolicy.setBackOffPeriod(2000);
		retryTemplate.setBackOffPolicy(backOffPolicy);
		return retryTemplate;
	}
	
	public static void main(String[] args) {
//		new SpringApplicationBuilder().
		SpringApplication.run(Application.class, args);
	}
}

