package com.mypeoject.app.authservice;

import com.mypeoject.app.authservice.model.Users;
import com.mypeoject.app.authservice.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootApplication
public class AuthenticationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthenticationServiceApplication.class, args);
    }
    @Bean
    public CommandLineRunner init(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        return args -> {
            // Check if admin user already exists
            Users adminUser = userRepository.findByUsername("admin");
            if (adminUser == null) {
                // Create the admin user
                Users admin = new Users();
                admin.setUsername("admin");
                admin.setPassword(passwordEncoder.encode("password"));

                // You can set additional properties for the admin user if needed

                // Save the admin user to the database
                userRepository.save(admin);
            }
        };
    }
}
