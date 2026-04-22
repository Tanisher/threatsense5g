package com.threatsense.controller;

import com.threatsense.model.User;
import com.threatsense.model.enums.Role;
import com.threatsense.repository.UserRepository;
import com.threatsense.service.AuditService;
import com.threatsense.service.UserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

@Controller
@RequestMapping("/admin")
public class AdminController {

    private final UserService userService;
    private final UserRepository userRepository;
    private final AuditService auditService;

    public AdminController(UserService userService, UserRepository userRepository, AuditService auditService) {
        this.userService = userService;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    private Optional<User> currentUser(UserDetails principal) {
        if (principal == null) return Optional.empty();
        return Optional.ofNullable(userRepository.findByUsername(principal.getUsername()));
    }

    @GetMapping("/users")
    public String users(Model model) {
        List<User> users = userService.getAllUsers();
        model.addAttribute("users", users);
        model.addAttribute("currentPage", "admin-users");
        return "admin/users";
    }

    @GetMapping("/users/new")
    public String newUserForm(Model model) {
        model.addAttribute("roles", Role.values());
        model.addAttribute("currentPage", "admin-users");
        return "admin/users/new";
    }

    @PostMapping("/users/new")
    public String createUser(@RequestParam String username,
                             @RequestParam String email,
                             @RequestParam String password,
                             @RequestParam Role role,
                             @AuthenticationPrincipal UserDetails principal,
                             RedirectAttributes redirectAttributes) {
        if (username == null || username.isBlank()) {
            redirectAttributes.addFlashAttribute("error", "Username is required.");
            redirectAttributes.addFlashAttribute("username", username);
            redirectAttributes.addFlashAttribute("email", email);
            return "redirect:/admin/users/new";
        }
        if (email == null || email.isBlank()) {
            redirectAttributes.addFlashAttribute("error", "Email is required.");
            redirectAttributes.addFlashAttribute("username", username);
            redirectAttributes.addFlashAttribute("email", email);
            return "redirect:/admin/users/new";
        }
        if (password == null || password.length() < 4) {
            redirectAttributes.addFlashAttribute("error", "Password must be at least 4 characters.");
            redirectAttributes.addFlashAttribute("username", username);
            redirectAttributes.addFlashAttribute("email", email);
            return "redirect:/admin/users/new";
        }
        if (userRepository.findByUsername(username) != null) {
            redirectAttributes.addFlashAttribute("error", "Username already exists.");
            redirectAttributes.addFlashAttribute("username", username);
            redirectAttributes.addFlashAttribute("email", email);
            return "redirect:/admin/users/new";
        }
        if (userRepository.findByEmail(email) != null) {
            redirectAttributes.addFlashAttribute("error", "Email already exists.");
            redirectAttributes.addFlashAttribute("username", username);
            redirectAttributes.addFlashAttribute("email", email);
            return "redirect:/admin/users/new";
        }
        User created = userService.createUser(username, email, password, role);
        currentUser(principal).ifPresent(u ->
                auditService.log(u, "USER_CREATED", "User", created.getId(), "Created user " + username));
        redirectAttributes.addFlashAttribute("success", "User created: " + username);
        return "redirect:/admin/users";
    }

    @PostMapping("/users/{id}/toggle-active")
    public String toggleActive(@PathVariable Long id,
                               @AuthenticationPrincipal UserDetails principal,
                               RedirectAttributes redirectAttributes) {
        Optional<User> target = userRepository.findById(id);
        if (target.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "User not found.");
            return "redirect:/admin/users";
        }
        userService.toggleUserActive(id);
        boolean nowActive = !target.get().isActive();
        currentUser(principal).ifPresent(u ->
                auditService.log(u, "USER_TOGGLE_ACTIVE", "User", id, "Set active=" + nowActive));
        redirectAttributes.addFlashAttribute("success", "User " + (nowActive ? "activated" : "deactivated") + ".");
        return "redirect:/admin/users";
    }

    @GetMapping("/audit")
    public String audit(@RequestParam(required = false) String username,
                       @RequestParam(required = false) String action,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                       @RequestParam(defaultValue = "0") int page,
                       Model model) {
        LocalDateTime fromTs = from != null ? from.atStartOfDay() : null;
        LocalDateTime toTs = to != null ? to.atTime(LocalTime.MAX) : null;
        Pageable pageable = PageRequest.of(Math.max(0, page), 20);
        Page<com.threatsense.model.AuditLog> auditPage = auditService.findFiltered(username, action, fromTs, toTs, pageable);
        model.addAttribute("auditPage", auditPage);
        model.addAttribute("filterUsername", username);
        model.addAttribute("filterAction", action);
        model.addAttribute("filterFrom", from);
        model.addAttribute("filterTo", to);
        model.addAttribute("currentPage", "admin-audit");
        return "admin/audit";
    }
}
