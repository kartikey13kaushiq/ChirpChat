package com.mypeoject.app.authservice.controller;

import com.mypeoject.app.authservice.model.Users;
import com.mypeoject.app.authservice.service.AuthService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public Users registerUser(@RequestBody Users users) {
        System.out.println("Kartikey---Hitting? register??");
        return authService.createUser(users.getUsername(), users.getPassword());
    }
    @PostMapping("/loginservice")
    public String loginUser(@RequestBody Users users) {
        System.out.println("Kartikey---Hitting? login??");
        return authService.login(users.getUsername(), users.getPassword());
    }
    // You can add more endpoints for authentication (e.g., login) based on your requirements
}
