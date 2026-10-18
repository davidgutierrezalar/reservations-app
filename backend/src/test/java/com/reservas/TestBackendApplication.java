package com.reservas;

import org.springframework.boot.SpringApplication;

/** Runs the app locally with a throwaway PostgreSQL container: ./mvnw spring-boot:test-run */
public class TestBackendApplication {

	public static void main(String[] args) {
		// Dev-only values, never used outside this local runner
		System.setProperty("app.jwt.secret", "local-dev-secret-local-dev-secret-0123");
		System.setProperty("app.admin.email", "admin@reservas.dev");
		System.setProperty("app.admin.password", "admin12345");
		SpringApplication.from(BackendApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
