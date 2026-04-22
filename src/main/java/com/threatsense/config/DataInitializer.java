package com.threatsense.config;

import com.threatsense.model.enums.Role;
import com.threatsense.repository.UserRepository;
import com.threatsense.service.UserService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final UserService userService;

    public DataInitializer(UserRepository userRepository, UserService userService) {
        this.userRepository = userRepository;
        this.userService = userService;
    }

    @Override
    public void run(String... args) {
        if (userRepository.count() == 0) {
            userService.createUser(
                    "admin",
                    "admin@threatsense.com",
                    "admin123",
                    Role.SUPER_ADMIN
            );
            System.out.println("Default admin user created: admin / admin123");
        }
    }
}

