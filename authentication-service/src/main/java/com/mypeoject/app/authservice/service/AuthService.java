package com.mypeoject.app.authservice.service;

import com.mypeoject.app.authservice.model.Users;
import com.mypeoject.app.authservice.util.JwtTokenUtil;
import com.mypeoject.app.authservice.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    @Autowired
    private JwtTokenUtil jwtTokenUtil;
    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public Users createUser(String username, String password) {
        Users users = new Users();
        users.setUsername(username);
        // You should encode the password before storing it in the database
        users.setPassword(passwordEncoder.encode(password));
        return userRepository.save(users);
    }

    public Users loginUser(String username, String password) {
        Users users = userRepository.findByUsername(username);
        if (users == null) {
            throw new UsernameNotFoundException("User not found with username: " + username);
        }

        // Check if the provided password matches the stored encoded password
        if (!passwordEncoder.matches(password, users.getPassword())) {
            throw new IllegalArgumentException("Invalid password");
        }

        return users;
    }
    public String login(String username, String password) {
        // Your authentication logic here

        Users users = userRepository.findByUsername(username);
        if (users == null) {
            throw new UsernameNotFoundException("User not found with username: " + username);
        }

        // Check if the provided password matches the stored encoded password
        if (!passwordEncoder.matches(password, users.getPassword())) {
            throw new IllegalArgumentException("Invalid password");
        }
        return jwtTokenUtil.generateToken(username);
    }
    // You can add more methods for authentication (e.g., login) based on your requirements
}
