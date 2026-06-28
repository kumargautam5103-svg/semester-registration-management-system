package com.srms.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Controller
@RequestMapping("/student")
public class StudentController {

    @Autowired private JdbcTemplate jdbc;

    @Value("${srms.upload.dir:uploads/fee_receipts}")
    private String uploadDir;

    private static final Set<String> ALLOWED_EXTS = Set.of("pdf", "jpg", "jpeg", "png");

    /* ── Dashboard ───────────────────────────────────────────────── */
    @GetMapping({"", "/"})
    public String dashboard(Authentication auth, Model model) {
        String username = auth.getName();

        List<Map<String, Object>> students = jdbc.queryForList("""
            SELECT s.student_id, s.roll_no, s.current_sem, s.Batch,
                   c.course_name, c.short_code, c.Duration, u.full_name, u.Email
            FROM students s
            JOIN courses c ON s.course_id = c.course_id
            JOIN users u ON s.user_id = u.user_id
            WHERE u.username = ?
            """, username);

        if (!students.isEmpty()) {
            Map<String, Object> student = students.get(0);
            model.addAttribute("student", student);
            long studentId = ((Number) student.get("student_id")).longValue();

            List<Map<String, Object>> regs = jdbc.queryForList("""
                SELECT r.reg_id, r.semester, r.acad_year, r.status,
                       r.submitted_at, r.remarks, r.fee_receipt_path
                FROM registrations r WHERE r.student_id = ?
                ORDER BY r.submitted_at DESC
                """, studentId);

            if (!regs.isEmpty()) {
                Map<String, Object> reg = regs.get(0);
                model.addAttribute("reg", reg);
                long regId = ((Number) reg.get("reg_id")).longValue();
                List<String> subjectNames = jdbc.queryForList("""
                    SELECT sub.subject_name FROM registration_subjects rs
                    JOIN subjects sub ON rs.subject_id = sub.subject_id WHERE rs.reg_id = ?
                    """, String.class, regId);
                model.addAttribute("reg_subjects", subjectNames);
            }
        }
        return "student_dashboard";
    }

    /* ── Registration form ───────────────────────────────────────── */
    @GetMapping("/register")
    public String registerForm(Authentication auth,
                                @RequestParam(required = false) Integer sem,
                                Model model) {
        String username = auth.getName();
        Map<String, Object> student = getStudent(username);
        if (student == null) return "redirect:/student";

        model.addAttribute("student", student);
        int currentSem = ((Number) student.get("current_sem")).intValue();
        int selectedSem = sem != null ? sem : currentSem;
        model.addAttribute("selected_sem", selectedSem);

        // Build semester list from Duration string e.g. "3 years / 6 semesters"
        List<Integer> allSems = buildSemList(student, currentSem);
        model.addAttribute("all_semesters", allSems);

        // Registration period
        List<Map<String, Object>> periods = jdbc.queryForList("""
            SELECT period_id, acad_year, is_open FROM registration_periods
            WHERE is_open = TRUE ORDER BY created_at DESC LIMIT 1
            """);
        boolean regOpen = !periods.isEmpty();
        String acadYear = regOpen ? (String) periods.get(0).get("acad_year") : "2024-25";
        model.addAttribute("acad_year", acadYear);
        model.addAttribute("reg_open", regOpen);

        // Existing registration for selected sem + year
        long studentId = ((Number) student.get("student_id")).longValue();
        long courseId  = ((Number) student.get("course_id")).longValue();
        List<Map<String, Object>> existing = jdbc.queryForList("""
            SELECT reg_id, status FROM registrations
            WHERE student_id = ? AND semester = ? AND acad_year = ?
            """, studentId, selectedSem, acadYear);
        model.addAttribute("existing", existing.isEmpty() ? null : existing.get(0));

        // subjects for the selected semester
        List<Map<String, Object>> subjects = jdbc.queryForList("""
            SELECT subject_id, subject_code, subject_name, is_elective
            FROM subjects WHERE course_id = ? AND semester = ?
            ORDER BY is_elective, subject_name
            """, courseId, selectedSem);
        model.addAttribute("subjects", subjects);

        return "student_register";
    }

    @PostMapping("/register")
    public String registerSubmit(Authentication auth,
                                  @RequestParam int semester,
                                  @RequestParam(required = false) List<String> subjects,
                                  @RequestParam(value = "fee_receipt", required = false) MultipartFile feeFile,
                                  RedirectAttributes ra) throws IOException {
        String username = auth.getName();
        Map<String, Object> student = getStudent(username);
        if (student == null) return "redirect:/student";

        long studentId = ((Number) student.get("student_id")).longValue();

        // Check period open
        List<Map<String, Object>> periods = jdbc.queryForList(
            "SELECT acad_year FROM registration_periods WHERE is_open=TRUE ORDER BY created_at DESC");
        if (periods.isEmpty()) {
            ra.addFlashAttribute("msg_type", "warn");
            ra.addFlashAttribute("msg", "No active registration period.");
            return "redirect:/student/register";
        }
        String acadYear = (String) periods.get(0).get("acad_year");

        // Check already registered
        List<Map<String, Object>> existing = jdbc.queryForList(
            "SELECT reg_id FROM registrations WHERE student_id=? AND semester=? AND acad_year=?",
            studentId, semester, acadYear);
        if (!existing.isEmpty()) {
            ra.addFlashAttribute("msg_type", "warn");
            ra.addFlashAttribute("msg", "You are already registered for this semester.");
            return "redirect:/student/register";
        }

        if (subjects == null || subjects.size() < 2) {
            ra.addFlashAttribute("msg_type", "warn");
            ra.addFlashAttribute("msg", "Please select at least 2 subjects.");
            return "redirect:/student/register?sem=" + semester;
        }

        // Handle file upload
        String feePath = null;
        if (feeFile != null && !feeFile.isEmpty()) {
            String origName = StringUtils.cleanPath(Objects.requireNonNull(feeFile.getOriginalFilename()));
            String ext = origName.contains(".") ? origName.substring(origName.lastIndexOf('.') + 1).toLowerCase() : "";
            if (!ALLOWED_EXTS.contains(ext)) {
                ra.addFlashAttribute("msg_type", "warn");
                ra.addFlashAttribute("msg", "Fee receipt must be PDF, JPG or PNG (max 5 MB).");
                return "redirect:/student/register?sem=" + semester;
            }
            String rollNo = ((String) student.get("roll_no")).replace("/", "_");
            String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
            String fname = rollNo + "_" + semester + "_" + ts + "_" + origName;
            Path destDir = Paths.get(uploadDir);
            Files.createDirectories(destDir);
            Files.copy(feeFile.getInputStream(), destDir.resolve(fname), StandardCopyOption.REPLACE_EXISTING);
            feePath = fname;
        }

        // Insert registration
        jdbc.update("""
            INSERT INTO registrations (student_id, semester, acad_year, status, fee_receipt_path)
            VALUES (?, ?, ?, 'pending', ?)
            """, studentId, semester, acadYear, feePath);

        List<Map<String, Object>> regRow = jdbc.queryForList(
            "SELECT lastval() AS regid");
        long regId = ((Number) regRow.get(0).get("regid")).longValue();

        for (String sid : subjects) {
            jdbc.update("INSERT INTO registration_subjects (reg_id, subject_id) VALUES (?,?)",
                        regId, Long.parseLong(sid));
        }

        ra.addFlashAttribute("msg_type", "success");
        ra.addFlashAttribute("msg", "Registration submitted successfully!");
        return "redirect:/student/register?sem=" + semester;
    }

    /* ── status ──────────────────────────────────────────────────── */
    @GetMapping("/status")
    public String status(Authentication auth, Model model) {
        String username = auth.getName();
        List<Map<String, Object>> sRows = jdbc.queryForList(
            "SELECT student_id FROM students s JOIN users u ON s.user_id=u.user_id WHERE u.username=?", username);
        if (sRows.isEmpty()) return "redirect:/student";

        long studentId = ((Number) sRows.get(0).get("student_id")).longValue();
        List<Map<String, Object>> regs = jdbc.queryForList("""
            SELECT r.reg_id, r.semester, r.acad_year, r.status,
                   r.submitted_at, r.reviewed_at, r.remarks, r.fee_receipt_path
            FROM registrations r WHERE r.student_id = ? ORDER BY r.submitted_at DESC
            """, studentId);

        List<Map<String, Object>> regList = new ArrayList<>();
        for (Map<String, Object> r : regs) {
            long regId = ((Number) r.get("reg_id")).longValue();
            List<String> subs = jdbc.queryForList("""
                SELECT sub.subject_name FROM registration_subjects rs
                JOIN subjects sub ON rs.subject_id=sub.subject_id WHERE rs.reg_id=?
                """, String.class, regId);
            Map<String, Object> entry = new HashMap<>(r);
            entry.put("subjects", subs);
            regList.add(entry);
        }
        model.addAttribute("regs", regList);
        return "student_status";
    }

    /* ── Helpers ─────────────────────────────────────────────────── */
    private Map<String, Object> getStudent(String username) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT s.student_id, s.roll_no, s.current_sem, s.course_id, s.Batch,
                   c.course_name, c.short_code, c.Duration, u.full_name, u.Email
            FROM students s
            JOIN courses c ON s.course_id = c.course_id
            JOIN users u ON s.user_id = u.user_id
            WHERE u.username = ?
            """, username);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private List<Integer> buildSemList(Map<String, Object> student, int currentSem) {
        int semCount = currentSem;
        try {
            String dur = (String) student.get("duration");
            if (dur != null) {
                for (String part : dur.replace("/", ",").split(",")) {
                    part = part.trim().toLowerCase();
                    if (part.contains("semester")) {
                        semCount = Integer.parseInt(part.replaceAll("[^0-9]", ""));
                        break;
                    }
                }
            }
        } catch (Exception ignored) {}
        List<Integer> sems = new ArrayList<>();
        for (int i = 1; i <= Math.max(semCount, currentSem); i++) sems.add(i);
        return sems;
    }
}
