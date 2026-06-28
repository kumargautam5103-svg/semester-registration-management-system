package com.srms.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Loads a user by username from the Users table.
 * Maps the Role column ('admin','coordinator','student') to Spring ROLE_ authorities.
 */
@Service
public class SrmsUserDetailsService implements UserDetailsService {

    @Autowired
    private JdbcTemplate jdbc;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT user_id, username, password_hash, role, full_name, email, is_active " +
            "FROM users WHERE username = ? AND is_active = TRUE", username);

        if (rows.isEmpty()) {
            throw new UsernameNotFoundException("User not found: " + username);
        }

        Map<String, Object> row = rows.get(0);
        String role = ((String) row.get("role")).toUpperCase();
        String password = (String) row.get("password_hash");

        return User.builder()
                .username(username)
                .password(password)
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_" + role)))
                .build();
    }
}
