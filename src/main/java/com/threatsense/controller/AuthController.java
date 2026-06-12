package com.threatsense.controller;

import com.threatsense.config.JwtUtil;
import com.threatsense.model.User;
import com.threatsense.repository.UserRepository;
import com.threatsense.service.AuditService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.Duration;

@Controller
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;
    private final AuditService auditService;

    public AuthController(AuthenticationManager authenticationManager,
                          JwtUtil jwtUtil,
                          UserRepository userRepository,
                          AuditService auditService) {
        this.authenticationManager = authenticationManager;
        this.jwtUtil = jwtUtil;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    @GetMapping("/login")
    public String loginPage() {
        return "auth/login";
    }

    @PostMapping("/login")
    public String login(@RequestParam String username,
                        @RequestParam String password,
                        HttpServletResponse response) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, password)
            );
            SecurityContextHolder.getContext().setAuthentication(authentication);

            String token = jwtUtil.generateToken(username);

            ResponseCookie cookie = ResponseCookie.from("jwt", token)
                    .httpOnly(true)
                    .path("/")
                    .maxAge(Duration.ofHours(24))
                    .build();

            response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

            User user = userRepository.findByUsername(username);
            if (user != null) {
                auditService.log(user, "LOGIN", "User", user.getId(), "User signed in");
            }

            return "redirect:/dashboard";
        } catch (AuthenticationException ex) {
            return "redirect:/login?error";
        }
    }

    @GetMapping("/logout")
    public String logout(@AuthenticationPrincipal UserDetails principal, HttpServletResponse response) {
        if (principal != null) {
            User user = userRepository.findByUsername(principal.getUsername());
            if (user != null) {
                auditService.log(user, "LOGOUT", "User", user.getId(), "User signed out");
            }
        }

        SecurityContextHolder.clearContext();

        ResponseCookie cookie = ResponseCookie.from("jwt", "")
                .httpOnly(true)
                .path("/")
                .maxAge(0)
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

        return "redirect:/login";
    }
}

