package com.ordercraft.accounts;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AccountService implements UserDetailsService, ApplicationRunner {

    private final JdbcTemplate db;
    private final PasswordEncoder encoder;
    private final String adminUsername;
    private final String adminPassword;

    public AccountService(
        JdbcTemplate db,
        PasswordEncoder encoder,
        @Value("${ordercraft.admin.username}") String username,
        @Value("${ordercraft.admin.password}") String password
    ) {
        this.db = db;
        this.encoder = encoder;
        this.adminUsername = username;
        this.adminPassword = password;
    }

    public static String normalize(String username) {
        return username.trim().toLowerCase(Locale.ROOT);
    }

    public static boolean validPassword(String password) {
        return (
            password != null &&
            password.length() >= 12 &&
            password.getBytes(StandardCharsets.UTF_8).length <= 72
        );
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        return db
            .query(
                "SELECT username,password_hash,role FROM app_users WHERE username=?",
                (rs, row) ->
                    User.withUsername(rs.getString("username"))
                        .password(rs.getString("password_hash"))
                        .roles(rs.getString("role"))
                        .build(),
                normalize(username)
            )
            .stream()
            .findFirst()
            .orElseThrow(() -> new UsernameNotFoundException("Account not found"));
    }

    public void create(String username, String password, String role) {
        db.update(
            "INSERT INTO app_users(username,password_hash,role) VALUES(?,?,?)",
            normalize(username),
            encoder.encode(password),
            role
        );
    }

    @Override
    public void run(ApplicationArguments args) {
        if (db.queryForObject("SELECT COUNT(*) FROM app_users", Integer.class) == 0) {
            if (
                !adminUsername.matches("[a-zA-Z0-9._-]{3,60}") || !validPassword(adminPassword)
            ) throw new IllegalStateException(
                "First startup requires ADMIN_USERNAME (3–60 letters, numbers, dots, underscores or hyphens) and ADMIN_PASSWORD (at least 12 characters, at most 72 UTF-8 bytes)."
            );
            create(adminUsername, adminPassword, "ADMIN");
        }
    }
}
