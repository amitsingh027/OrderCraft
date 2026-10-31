package com.ordercraft;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
public class AccountController {
    private final AccountService accounts;
    private final JdbcTemplate db;
    public AccountController(AccountService accounts, JdbcTemplate db) { this.accounts=accounts; this.db=db; }

    public record NewUser(@NotBlank @Pattern(regexp="[a-zA-Z0-9._-]{3,60}") String username,
                          @NotNull @Size(min=12,max=72) String password,
                          @NotNull @Pattern(regexp="ADMIN|SALES|PRODUCTION|ACCOUNTS") String role) {}

    @GetMapping("/auth/csrf")
    public Map<String,String> csrf(CsrfToken token) {
        return Map.of("token", token.getToken(), "headerName", token.getHeaderName());
    }

    @GetMapping("/auth/me")
    public Map<String,String> me(Authentication auth) {
        return Map.of("username",auth.getName(),
            "role",auth.getAuthorities().iterator().next().getAuthority().substring(5));
    }

    public record UserSummary(String username,String role,String createdAt){}
    @GetMapping("/admin/users")
    public List<UserSummary> users() {
        return db.query("SELECT username,role,created_at FROM app_users ORDER BY username",
            (rs,n)->new UserSummary(rs.getString("username"),rs.getString("role"),rs.getTimestamp("created_at").toInstant().toString()));
    }

    @PostMapping("/admin/users")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String,String> create(@Valid @RequestBody NewUser body) {
        if (!AccountService.validPassword(body.password()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Password must contain at least 12 characters and no more than 72 UTF-8 bytes.");
        accounts.create(body.username(),body.password(),body.role());
        return Map.of("username",AccountService.normalize(body.username()),"role",body.role());
    }
}
