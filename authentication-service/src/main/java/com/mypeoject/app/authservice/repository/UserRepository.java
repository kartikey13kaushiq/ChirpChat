package com.mypeoject.app.authservice.repository;

import com.mypeoject.app.authservice.model.Users;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<Users, Long> {
    Users findByUsername(String username);
}
