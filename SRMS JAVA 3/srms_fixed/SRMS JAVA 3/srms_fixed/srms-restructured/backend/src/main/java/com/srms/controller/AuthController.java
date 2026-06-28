package com.srms.controller;

import com.srms.service.EmailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Controller
public class AuthController {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private EmailService emailService;

    @Value("${server.port:8080}")
    private int serverPort;

    /* ── Root redirect ───────────────────────────────────────────── */
    @GetMapping("/")
    public String index(Authentication auth) {
        return auth != null ? "redirect:/dashboard" : "redirect:/login";
    }

    /* ── Dashboard router ────────────────────────────────────────── */
    @GetMapping("/dashboard")
    public String dashboard(Authentication auth) {
        if (auth == null) return "redirect:/login";
        if (auth.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_STUDENT")))
            return "redirect:/student";
        if (auth.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_COORDINATOR")))
            return "redirect:/coordinator";
        if (auth.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_ADMIN")))
            return "redirect:/admin";
        return "redirect:/login";
    }

    /* ── Login page (GET only; POST handled by Spring Security) ──── */
    @GetMapping("/login")
    public String loginPage(@RequestParam(required = false) String error, Model model) {
        if (error != null) model.addAttribute("error", "Invalid credentials. Please try again.");
        return "index";
    }

    /* ── Forgot Password ─────────────────────────────────────────── */
    @GetMapping("/forgot-password")
    public String forgotPasswordPage() {
        return "forgot_password";
    }

    @PostMapping("/forgot-password")
    public String forgotPasswordSubmit(@RequestParam String email, Model model) {
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT user_id, full_name, Email FROM users WHERE Email=? AND is_active=TRUE", email);

            if (!rows.isEmpty()) {
                Map<String, Object> user = rows.get(0);
                int userId = (int) user.get("user_id");
                String fullName = (String) user.get("full_name");

                // Generate token
                byte[] bytes = new byte[32];
                new SecureRandom().nextBytes(bytes);
                String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
                LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(30);

                // Delete existing tokens for this user, insert new one
                jdbc.update("DELETE FROM password_reset_tokens WHERE user_id=?", userId);
                jdbc.update(
                    "INSERT INTO password_reset_tokens (user_id, Token, expires_at) VALUES (?,?,?)",
                    userId, token, expiresAt);

                String resetUrl = "http://localhost:" + serverPort + "/reset-password/" + token;
                emailService.sendPasswordReset(email, fullName, resetUrl);
            }
        } catch (Exception e) {
            System.err.println("[FORGOT PW ERROR] " + e.getMessage());
        }
        model.addAttribute("sent", true);
        model.addAttribute("email", email);
        return "forgot_password";
    }

    /* ── Reset Password ──────────────────────────────────────────── */
    @GetMapping("/reset-password/{token}")
    public String resetPasswordPage(@PathVariable String token, Model model) {
        try {
            List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT prt.user_id, u.full_name, prt.expires_at
                FROM password_reset_tokens prt
                JOIN users u ON u.user_id = prt.user_id
                WHERE prt.Token = ?
                """, token);

            if (rows.isEmpty()) {
                model.addAttribute("error", "Invalid or expired reset link.");
                return "reset_password";
            }
            LocalDateTime expiresAt = ((java.sql.Timestamp) rows.get(0).get("expires_at")).toLocalDateTime();
            if (LocalDateTime.now().isAfter(expiresAt)) {
                jdbc.update("DELETE FROM password_reset_tokens WHERE Token=?", token);
                model.addAttribute("error", "This reset link has expired. Please request a new one.");
                return "reset_password";
            }
            model.addAttribute("token", token);
        } catch (Exception e) {
            model.addAttribute("error", "An error occurred: " + e.getMessage());
        }
        return "reset_password";
    }

    @PostMapping("/reset-password/{token}")
    public String resetPasswordSubmit(@PathVariable String token,
                                       @RequestParam String password,
                                       @RequestParam String confirm_password,
                                       Model model,
                                       RedirectAttributes redirectAttrs) {
        model.addAttribute("token", token);
        if (password.length() < 6) {
            model.addAttribute("error", "Password must be at least 6 characters.");
            return "reset_password";
        }
        if (!password.equals(confirm_password)) {
            model.addAttribute("error", "Passwords do not match.");
            return "reset_password";
        }
        try {
            List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT prt.user_id, prt.expires_at FROM password_reset_tokens prt WHERE prt.Token = ?
                """, token);
            if (rows.isEmpty()) {
                model.addAttribute("error", "Invalid or expired reset link.");
                return "reset_password";
            }
            int userId = (int) rows.get(0).get("user_id");
            jdbc.update("UPDATE users SET password_hash=? WHERE user_id=?", password, userId);
            jdbc.update("DELETE FROM password_reset_tokens WHERE user_id=?", userId);
            model.addAttribute("success", true);
        } catch (Exception e) {
            model.addAttribute("error", "An error occurred: " + e.getMessage());
        }
        return "reset_password";
    }
}
