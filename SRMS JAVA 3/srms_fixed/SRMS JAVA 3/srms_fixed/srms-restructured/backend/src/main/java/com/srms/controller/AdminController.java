package com.srms.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Controller
@RequestMapping("/admin")
public class AdminController {

    @Autowired private JdbcTemplate jdbc;

    /* ── Dashboard ───────────────────────────────────────────────── */
    @GetMapping({"", "/"})
    public String dashboard(Model model) {
        model.addAttribute("total_students",  jdbc.queryForObject("SELECT COUNT(*) FROM students", Long.class));
        model.addAttribute("total_coords",    jdbc.queryForObject("SELECT COUNT(*) FROM coordinators", Long.class));
        model.addAttribute("pending",         jdbc.queryForObject("SELECT COUNT(*) FROM registrations WHERE status='pending'", Long.class));
        model.addAttribute("total_courses",   jdbc.queryForObject("SELECT COUNT(*) FROM courses", Long.class));
        model.addAttribute("total_subjects",  jdbc.queryForObject("SELECT COUNT(*) FROM subjects", Long.class));

       List<Map<String, Object>> periods = jdbc.queryForList("""
    SELECT period_id,
           acad_year,
           is_open,
           start_date,
           end_date,
           created_at
    FROM registration_periods
    ORDER BY created_at DESC
    """);
        model.addAttribute("reg_period", periods.isEmpty() ? null : periods.get(0));
        boolean hasOpenPeriod = false;

for (Map<String, Object> p : periods) {

    Object open = p.get("is_open");

    if (Boolean.TRUE.equals(open) ||
        Integer.valueOf(1).equals(open)) {

        hasOpenPeriod = true;
        break;
    }
}
        model.addAttribute("all_periods", periods);
        model.addAttribute("hasOpenPeriod", hasOpenPeriod);
        return "admin_dashboard";
    }

    /* ── Registration Period ─────────────────────────────────────── */
    @PostMapping("/registration-period")
    public String toggleRegPeriod(@RequestParam String action,
                                   @RequestParam(required = false) String acad_year,
                                   @RequestParam(required = false) String start_date,
                                   @RequestParam(required = false) String end_date,
                                   RedirectAttributes ra) {
        if ("open".equals(action)) {
            jdbc.update("UPDATE registration_periods SET is_open=FALSE WHERE is_open=TRUE");
            jdbc.update(
    "INSERT INTO registration_periods (acad_year, is_open, start_date, end_date) VALUES (?, TRUE, ?, ?)",
    acad_year,
    (start_date != null && !start_date.isBlank())
            ? java.sql.Date.valueOf(start_date)
            : null,
    (end_date != null && !end_date.isBlank())
            ? java.sql.Date.valueOf(end_date)
            : null
);
            ra.addFlashAttribute("flash_type", "success");
            ra.addFlashAttribute("flash", "Registration period opened for " + acad_year + ".");
        } else {
            jdbc.update("UPDATE registration_periods SET is_open=FALSE WHERE is_open=TRUE");
            ra.addFlashAttribute("flash_type", "success");
            ra.addFlashAttribute("flash", "Registration period closed.");
        }
        return "redirect:/admin";
    }

    @PostMapping("/registration-period/edit/{periodId}")
    public String editRegPeriod(@PathVariable long periodId,
                                 @RequestParam String acad_year,
                                 @RequestParam(required = false) String start_date,
                                 @RequestParam(required = false) String end_date,
                                 @RequestParam(required = false) String is_open,
                                 RedirectAttributes ra) {
        boolean open = is_open != null;
        try {
            if (open) jdbc.update("UPDATE registration_periods SET is_open=FALSE WHERE period_id!=?", periodId);
            jdbc.update(
    "UPDATE registration_periods SET acad_year=?, start_date=?, end_date=?, is_open=? WHERE period_id=?",
    acad_year,
    (start_date != null && !start_date.isBlank())
            ? java.sql.Date.valueOf(start_date)
            : null,
    (end_date != null && !end_date.isBlank())
            ? java.sql.Date.valueOf(end_date)
            : null,
    open,
    periodId
);
            ra.addFlashAttribute("flash_type", "success");
            ra.addFlashAttribute("flash", "Period \"" + acad_year + "\" updated.");
        } catch (Exception e) {
            ra.addFlashAttribute("flash_type", "danger");
            ra.addFlashAttribute("flash", "Error: " + e.getMessage());
        }
        return "redirect:/admin";
    }

    @PostMapping("/registration-period/delete/{periodId}")
    public String deleteRegPeriod(@PathVariable long periodId, RedirectAttributes ra) {
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT acad_year FROM registration_periods WHERE period_id=?", periodId);
            if (rows.isEmpty()) { ra.addFlashAttribute("flash_type", "danger"); ra.addFlashAttribute("flash", "Period not found."); return "redirect:/admin"; }
            jdbc.update("DELETE FROM registration_periods WHERE period_id=?", periodId);
            ra.addFlashAttribute("flash_type", "success");
            ra.addFlashAttribute("flash", "Period \"" + rows.get(0).get("acad_year") + "\" deleted.");
        } catch (Exception e) {
            ra.addFlashAttribute("flash_type", "danger");
            ra.addFlashAttribute("flash", "Error: " + e.getMessage());
        }
        return "redirect:/admin";
    }

    /* ── users ───────────────────────────────────────────────────── */
    @GetMapping("/users")
    public String users(Model model) {
        List<Map<String, Object>> users = jdbc.queryForList("""
            SELECT u.user_id, u.username, u.full_name, u.Email, u.Role,
                   u.is_active, u.created_at,
                   s.roll_no, c.short_code, s.current_sem,
                   co.coord_id, co.department
            FROM users u
            LEFT JOIN students s ON s.user_id=u.user_id
            LEFT JOIN courses c ON c.course_id=s.course_id
            LEFT JOIN coordinators co ON co.user_id=u.user_id
            WHERE u.Role IN ('student','coordinator')
            ORDER BY u.Role, u.full_name
            """);

        Map<Long, List<Map<String, Object>>> coordAssignments = new HashMap<>();
        for (Map<String, Object> u : users) {
            if ("coordinator".equals(u.get("role")) && u.get("coord_id") != null) {
                long coordId = ((Number) u.get("coord_id")).longValue();
                List<Map<String, Object>> asgn = jdbc.queryForList("""
                    SELECT ca.assign_id, c.short_code, ca.semester
                    FROM coordinator_assignments ca JOIN courses c ON ca.course_id=c.course_id
                    WHERE ca.coord_id=? ORDER BY c.short_code, ca.semester
                    """, coordId);
                coordAssignments.put(coordId, asgn);
            }
        }
        model.addAttribute("users", users);
        model.addAttribute("coord_assignments", coordAssignments);
        model.addAttribute("courses", jdbc.queryForList("SELECT course_id, course_name, short_code, Duration FROM courses ORDER BY short_code"));
        model.addAttribute("coordinators", jdbc.queryForList("""
            SELECT co.coord_id, u.full_name, co.department
            FROM coordinators co JOIN users u ON co.user_id=u.user_id
            WHERE u.is_active=TRUE ORDER BY u.full_name
            """));
        return "admin_users";
    }

    @PostMapping("/users/add")
    public String addUser(@RequestParam String full_name, @RequestParam String username,
                           @RequestParam String email, @RequestParam String role,
                           @RequestParam(defaultValue = "pass123") String password,
                           @RequestParam(required = false) String course_id,
                           @RequestParam(defaultValue = "BCA") String short_code,
                           @RequestParam(defaultValue = "1") int init_sem,
                           @RequestParam(required = false) String department,
                           RedirectAttributes ra) {
        try {
            jdbc.update("INSERT INTO users (username, password_hash, Role, full_name, Email) VALUES (?,?,?,?,?)",
                username, password, role, full_name, email);
            List<Map<String, Object>> rows = jdbc.queryForList("SELECT lastval() AS uid");
            long uid = ((Number) rows.get(0).get("uid")).longValue();
            if ("student".equals(role) && course_id != null) {
                String roll = short_code + "/" + LocalDate.now().getYear() + "/" + String.format("%03d", uid);
                int year = LocalDate.now().getYear();
                jdbc.update("INSERT INTO students (user_id, roll_no, course_id, current_sem, Batch) VALUES (?,?,?,?,?)",
                    uid, roll, Long.parseLong(course_id), init_sem, year + "-" + (year + 3));
            } else if ("coordinator".equals(role)) {
                jdbc.update("INSERT INTO coordinators (user_id, department) VALUES (?,?)", uid, department != null ? department : "");
            }
            ra.addFlashAttribute("flash_type", "success");
            ra.addFlashAttribute("flash", "User created successfully.");
        } catch (Exception e) {
            ra.addFlashAttribute("flash_type", "danger");
            ra.addFlashAttribute("flash", "Error: " + e.getMessage());
        }
        return "redirect:/admin/users";
    }

    @PostMapping("/users/delete/{userId}")
    public String deactivateUser(@PathVariable long userId, @RequestParam(defaultValue = "deactivate") String action, RedirectAttributes ra) {
        if ("activate".equals(action)) {
            jdbc.update("UPDATE users SET is_active=TRUE WHERE user_id=?", userId);
            ra.addFlashAttribute("flash_type", "success");
            ra.addFlashAttribute("flash", "User activated.");
        } else {
            jdbc.update("UPDATE users SET is_active=FALSE WHERE user_id=?", userId);
            ra.addFlashAttribute("flash_type", "success");
            ra.addFlashAttribute("flash", "User deactivated.");
        }
        return "redirect:/admin/users";
    }

    @GetMapping("/users/edit/{userId}")
    public String editUserForm(@PathVariable long userId, Model model) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT u.user_id, u.username, u.full_name, u.Email, u.Role, u.is_active,
                   s.roll_no, s.course_id, s.current_sem, s.Batch, co.department
            FROM users u
            LEFT JOIN students s ON s.user_id=u.user_id
            LEFT JOIN coordinators co ON co.user_id=u.user_id
            WHERE u.user_id=?
            """, userId);
        if (rows.isEmpty()) return "redirect:/admin/users";
        model.addAttribute("user", rows.get(0));
        model.addAttribute("courses", jdbc.queryForList("SELECT course_id, course_name, short_code, Duration FROM courses ORDER BY short_code"));
        return "admin_edit_user";
    }

    @PostMapping("/users/edit/{userId}")
    public String editUserSubmit(@PathVariable long userId,
                                  @RequestParam String full_name, @RequestParam String username,
                                  @RequestParam String email, @RequestParam(required = false) String is_active,
                                  @RequestParam(required = false) String password,
                                  @RequestParam(required = false) String department,
                                  @RequestParam(required = false) String course_id,
                                  @RequestParam(required = false) String current_sem,
                                  @RequestParam(required = false) String batch,
                                  RedirectAttributes ra) {
        try {
            boolean active = is_active != null;
            if (password != null && !password.isBlank()) {
                jdbc.update("UPDATE users SET full_name=?, username=?, Email=?, is_active=?, password_hash=? WHERE user_id=?",
                    full_name, username, email, active, password, userId);
            } else {
                jdbc.update("UPDATE users SET full_name=?, username=?, Email=?, is_active=? WHERE user_id=?",
                    full_name, username, email, active, userId);
            }
            List<Map<String, Object>> roleRow = jdbc.queryForList("SELECT role FROM users WHERE user_id=?", userId);
            if (!roleRow.isEmpty()) {
                String role = (String) roleRow.get(0).get("role");
                if ("coordinator".equals(role) && department != null) jdbc.update("UPDATE coordinators SET department=? WHERE user_id=?", department, userId);
                if ("student".equals(role)) {
                    if (course_id != null) jdbc.update("UPDATE students SET course_id=? WHERE user_id=?", Long.parseLong(course_id), userId);
                    if (current_sem != null) jdbc.update("UPDATE students SET current_sem=? WHERE user_id=?", Integer.parseInt(current_sem), userId);
                    if (batch != null && !batch.isBlank()) jdbc.update("UPDATE students SET Batch=? WHERE user_id=?", batch, userId);
                }
            }
            ra.addFlashAttribute("flash_type", "success");
            ra.addFlashAttribute("flash", "User updated successfully.");
        } catch (Exception e) {
            ra.addFlashAttribute("flash_type", "danger");
            ra.addFlashAttribute("flash", "Error: " + e.getMessage());
        }
        return "redirect:/admin/users";
    }

    @PostMapping("/users/hard-delete/{userId}")
    public String hardDeleteUser(@PathVariable long userId, RedirectAttributes ra) {
        try {
            jdbc.update("DELETE FROM registration_subjects WHERE reg_id IN (SELECT r.reg_id FROM registrations r JOIN students s ON r.student_id=s.student_id WHERE s.user_id=?)", userId);
            jdbc.update("DELETE FROM registrations WHERE student_id IN (SELECT student_id FROM students WHERE user_id=?)", userId);
            jdbc.update("DELETE FROM students WHERE user_id=?", userId);
            jdbc.update("DELETE FROM coordinator_assignments WHERE coord_id IN (SELECT coord_id FROM coordinators WHERE user_id=?)", userId);
            jdbc.update("DELETE FROM coordinators WHERE user_id=?", userId);
            jdbc.update("DELETE FROM password_reset_tokens WHERE user_id=?", userId);
            jdbc.update("DELETE FROM users WHERE user_id=?", userId);
            ra.addFlashAttribute("flash_type", "success");
            ra.addFlashAttribute("flash", "User permanently deleted.");
        } catch (Exception e) {
            ra.addFlashAttribute("flash_type", "danger");
            ra.addFlashAttribute("flash", "Error: " + e.getMessage());
        }
        return "redirect:/admin/users";
    }

    /* ── Coordinator Assignments ─────────────────────────────────── */
    @PostMapping("/coordinator/assign")
    public String assignCoordinator(@RequestParam String coord_id, @RequestParam String assign_course_id,
                                     @RequestParam String assign_semester, RedirectAttributes ra) {
        try {
            jdbc.update("""
                INSERT INTO coordinator_assignments (coord_id, course_id, semester) VALUES (?,?,?) ON CONFLICT (coord_id, course_id, semester) DO NOTHING
                """, coord_id, assign_course_id, assign_semester);
            ra.addFlashAttribute("flash_type", "success");
            ra.addFlashAttribute("flash", "Assignment added successfully.");
        } catch (Exception e) {
            ra.addFlashAttribute("flash_type", "danger");
            ra.addFlashAttribute("flash", "Error: " + e.getMessage());
        }
        return "redirect:/admin/users";
    }

    @PostMapping("/coordinator/unassign")
    public String unassignCoordinator(@RequestParam long assign_id, RedirectAttributes ra) {
        try {
            jdbc.update("DELETE FROM coordinator_assignments WHERE assign_id=?", assign_id);
            ra.addFlashAttribute("flash_type", "success");
            ra.addFlashAttribute("flash", "Assignment removed.");
        } catch (Exception e) {
            ra.addFlashAttribute("flash_type", "danger");
            ra.addFlashAttribute("flash", "Error: " + e.getMessage());
        }
        return "redirect:/admin/users";
    }

    /* ── courses ─────────────────────────────────────────────────── */
    @GetMapping("/courses")
    public String courses(Model model) {
        model.addAttribute("courses", jdbc.queryForList("""
            SELECT c.course_id, c.course_name, c.short_code, c.Duration, COUNT(s.student_id) AS student_count
            FROM courses c LEFT JOIN students s ON s.course_id=c.course_id
            GROUP BY c.course_id, c.course_name, c.short_code, c.Duration
            """));
        return "admin_courses";
    }

    @PostMapping("/courses/add")
    public String addCourse(@RequestParam String course_name, @RequestParam String short_code,
                             @RequestParam String duration, RedirectAttributes ra) {
        try {
            jdbc.update("INSERT INTO courses (course_name, short_code, Duration) VALUES (?,?,?)",
                course_name, short_code.toUpperCase(), duration);
            ra.addFlashAttribute("flash_type", "success");
            ra.addFlashAttribute("flash", "Course added.");
        } catch (Exception e) {
            ra.addFlashAttribute("flash_type", "danger");
            ra.addFlashAttribute("flash", "Error: " + e.getMessage());
        }
        return "redirect:/admin/courses";
    }

    @PostMapping("/courses/delete/{courseId}")
    public String deleteCourse(@PathVariable long courseId, RedirectAttributes ra) {
        try {
            jdbc.update("DELETE FROM courses WHERE course_id=?", courseId);
            ra.addFlashAttribute("flash_type", "success");
            ra.addFlashAttribute("flash", "Course deleted.");
        } catch (Exception e) {
            ra.addFlashAttribute("flash_type", "danger");
            ra.addFlashAttribute("flash", "Cannot delete — course may have students/subjects: " + e.getMessage());
        }
        return "redirect:/admin/courses";
    }

    /* ── subjects ────────────────────────────────────────────────── */
    @GetMapping("/subjects")
    public String subjects(@RequestParam(required = false) String course, Model model) {
        StringBuilder sql = new StringBuilder("""
            SELECT sub.subject_id, sub.subject_code, sub.subject_name,
                   c.short_code, sub.semester, sub.is_elective
            FROM subjects sub JOIN courses c ON sub.course_id=c.course_id WHERE 1=1
            """);
        List<Object> params = new ArrayList<>();
        if (course != null && !course.isBlank()) { sql.append(" AND c.short_code=?"); params.add(course); }
        sql.append(" ORDER BY c.short_code, sub.semester, sub.subject_name");
        model.addAttribute("subjects", jdbc.queryForList(sql.toString(), params.toArray()));
        model.addAttribute("courses", jdbc.queryForList("SELECT course_id, short_code FROM courses ORDER BY short_code"));
        model.addAttribute("course_filter", course);
        return "admin_subjects";
    }

    @PostMapping("/subjects/add")
    public String addSubject(@RequestParam String subject_name, @RequestParam String subject_code,
                              @RequestParam String course_id, @RequestParam String semester,
                              @RequestParam(required = false) String is_elective, RedirectAttributes ra) {
        try {
            jdbc.update("INSERT INTO subjects (subject_code, subject_name, course_id, semester, is_elective) VALUES (?,?,?,?,?)",
                subject_code, subject_name, course_id, semester, is_elective != null);
            ra.addFlashAttribute("flash_type", "success");
            ra.addFlashAttribute("flash", "Subject added.");
        } catch (Exception e) {
            ra.addFlashAttribute("flash_type", "danger");
            ra.addFlashAttribute("flash", "Error: " + e.getMessage());
        }
        return "redirect:/admin/subjects";
    }

    @PostMapping("/subjects/delete/{subjectId}")
    public String deleteSubject(@PathVariable long subjectId, RedirectAttributes ra) {
        try {
            List<Map<String, Object>> subRows = jdbc.queryForList(
                "SELECT subject_name, is_elective FROM subjects WHERE subject_id=?", subjectId);
            if (subRows.isEmpty()) { ra.addFlashAttribute("flash_type", "danger"); ra.addFlashAttribute("flash", "Subject not found."); return "redirect:/admin/subjects"; }
            boolean isElective = Boolean.TRUE.equals(subRows.get(0).get("is_elective"))
                || Integer.valueOf(1).equals(subRows.get(0).get("is_elective"));
            String name = (String) subRows.get(0).get("subject_name");
            if (isElective) {
                jdbc.update("DELETE FROM registration_subjects WHERE subject_id=?", subjectId);
                jdbc.update("DELETE FROM subjects WHERE subject_id=?", subjectId);
                ra.addFlashAttribute("flash_type", "success");
                ra.addFlashAttribute("flash", "Elective \"" + name + "\" deleted and removed from all registrations.");
            } else {
                long refCount = jdbc.queryForObject("SELECT COUNT(*) FROM registration_subjects WHERE subject_id=?", Long.class, subjectId);
                if (refCount > 0) {
                    ra.addFlashAttribute("flash_type", "danger");
                    ra.addFlashAttribute("flash", "Cannot delete core subject \"" + name + "\" — it is referenced in " + refCount + " registration(s).");
                } else {
                    jdbc.update("DELETE FROM subjects WHERE subject_id=?", subjectId);
                    ra.addFlashAttribute("flash_type", "success");
                    ra.addFlashAttribute("flash", "Subject \"" + name + "\" deleted.");
                }
            }
        } catch (Exception e) {
            ra.addFlashAttribute("flash_type", "danger");
            ra.addFlashAttribute("flash", "Error: " + e.getMessage());
        }
        return "redirect:/admin/subjects";
    }
}
