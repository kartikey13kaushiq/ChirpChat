package com.mypeoject.app.authservice.config;

import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

//import org.h2.server.web.WebServlet;

@Configuration
@EnableWebSecurity
public class SecurityConfig extends WebSecurityConfigurerAdapter {
//public class SecurityConfig{
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        //,"/authentication-service/**","/swagger-ui/**", "/v3/api-docs/**", "/swagger-ui.html","/h2-console/**"
        http.authorizeRequests()
                .antMatchers("/register*","/hello").permitAll()
                .antMatchers("/login*").permitAll()
                .antMatchers("/swagger-ui/**", "/v3/api-docs/**", "/swagger-ui.html").permitAll()
                .anyRequest().authenticated()
                .and().csrf().disable();
    }

    /*
    @Override
    protected void configure(HttpSecurity http) throws Exception {
        //,"/authentication-service/**","/swagger-ui/**", "/v3/api-docs/**", "/swagger-ui.html","/h2-console/**"
        http.authorizeRequests()
                .antMatchers("/**").permitAll()
                .anyRequest().authenticated()
                .and().csrf().disable();

    }
    */

}
