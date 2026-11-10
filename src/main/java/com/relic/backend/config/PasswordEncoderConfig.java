package com.relic.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class PasswordEncoderConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        // BCrypt: adaptive (tunable work factor) and salts automatically,
        // unlike a plain fast hash (SHA-256/MD5) which would make
        // brute-forcing leaked hashes far cheaper.
        return new BCryptPasswordEncoder();
    }
}
