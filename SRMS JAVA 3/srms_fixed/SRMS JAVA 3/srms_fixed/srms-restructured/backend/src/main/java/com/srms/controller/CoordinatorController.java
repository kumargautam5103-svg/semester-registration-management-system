package com.srms.controller;

import com.srms.service.EmailService;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

@Controller
@RequestMapping("/coordinator")
public class CoordinatorController {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private EmailService emailService;

    @Value("${srms.upload.dir:uploads/fee_receipts}")
    private String uploadDir;

    /* ── Dashboard ───────────────────────────────────────────────── */
    @GetMapping({"", "/"})
    public String dashboard(Authentication auth, Model model) {
        long coordId = getCoordId(auth.getName());
        Map<String, Object> coordInfo = jdbc.queryForList("""
            SELECT u.full_name, u.Email, co.department, co.coord_id
            FROM coordinators co JOIN users u ON co.user_id=u.user_id
            JOIN users ua ON ua.username=?
            WHERE co.user_id=ua.user_id
            """, auth.getName()).stream().findFirst().orElse(null);
        model.addAttribute("coord_info", coordInfo);

        List<Map<String, Object>> assignments = getAssignments(coordId);
        model.addAttribute("assignments", assignments);

        long pending = 0, approved = 0, scopedStudents = 0;
        if (coordId > 0) {
            pending = jdbc.queryForObject("""
                SELECT COUNT(*) FROM registrations r
                JOIN students s ON r.student_id=s.student_id
                WHERE r.status='pending'
                  AND EXISTS (SELECT 1 FROM coordinator_assignments ca
                              WHERE ca.coord_id=? AND ca.course_id=s.course_id AND ca.semester=r.semester)
                """, Long.class, coordId);
            approved = jdbc.queryForObject("""
                SELECT COUNT(*) FROM registrations r
                JOIN students s ON r.student_id=s.student_id
                WHERE r.status='approved'
                  AND EXISTS (SELECT 1 FROM coordinator_assignments ca
                              WHERE ca.coord_id=? AND ca.course_id=s.course_id AND ca.semester=r.semester)
                """, Long.class, coordId);
            scopedStudents = jdbc.queryForObject("""
                SELECT COUNT(DISTINCT s.student_id) FROM students s
                WHERE EXISTS (SELECT 1 FROM coordinator_assignments ca
                              WHERE ca.coord_id=? AND ca.course_id=s.course_id AND ca.semester=s.current_sem)
                """, Long.class, coordId);
        }
        model.addAttribute("pending", pending);
        model.addAttribute("approved", approved);
        model.addAttribute("scoped_students", scopedStudents);

        List<Map<String, Object>> periods = jdbc.queryForList(
            "SELECT acad_year, is_open FROM registration_periods WHERE is_open=TRUE ORDER BY created_at DESC");
        model.addAttribute("reg_open", !periods.isEmpty());
        model.addAttribute("acad_year", periods.isEmpty() ? null : periods.get(0).get("acad_year"));

        if (coordId > 0) {
            List<Map<String, Object>> recent = jdbc.queryForList(
                """
                SELECT u.full_name, r.status, r.submitted_at, c.short_code, r.semester
                FROM registrations r
                JOIN students s ON r.student_id=s.student_id
                JOIN users u ON s.user_id=u.user_id
                JOIN courses c ON s.course_id=c.course_id
                WHERE EXISTS (SELECT 1 FROM coordinator_assignments ca
                              WHERE ca.coord_id=? AND ca.course_id=s.course_id AND ca.semester=r.semester)
                ORDER BY r.submitted_at DESC
                LIMIT 5
                """, coordId);
            model.addAttribute("recent", recent);
        }
        return "coordinator_dashboard";
    }

    /* ── Initiate Registration (send bulk email) ─────────────────── */
    @PostMapping("/initiate-registration")
    public String initiateRegistration(Authentication auth, RedirectAttributes ra) {
        long coordId = getCoordId(auth.getName());
        List<Map<String, Object>> periods = jdbc.queryForList(
            "SELECT acad_year FROM registration_periods WHERE is_open=TRUE ORDER BY created_at DESC");
        if (periods.isEmpty()) {
            ra.addFlashAttribute("flash_type", "danger");
            ra.addFlashAttribute("flash", "No active registration period. Ask Admin to open one first.");
            return "redirect:/coordinator";
        }
        String acadYear = (String) periods.get(0).get("acad_year");
        List<Map<String, Object>> students = jdbc.queryForList("""
            SELECT DISTINCT u.Email, u.full_name, c.course_name, s.current_sem
            FROM students s
            JOIN users u ON s.user_id=u.user_id
            JOIN courses c ON s.course_id=c.course_id
            WHERE u.is_active=TRUE AND u.Email IS NOT NULL AND u.Email <> ''
              AND EXISTS (SELECT 1 FROM coordinator_assignments ca
                          WHERE ca.coord_id=? AND ca.course_id=s.course_id AND ca.semester=s.current_sem)
            """, coordId);
        if (students.isEmpty()) {
            ra.addFlashAttribute("flash_type", "warn");
            ra.addFlashAttribute("flash", "No students with valid emails found in your assigned courses.");
            return "redirect:/coordinator";
        }
        int sent = 0, failed = 0;
        for (Map<String, Object> s : students) {
            boolean ok = emailService.sendRegistrationInitiated(
                (String) s.get("email"), (String) s.get("full_name"),
                acadYear, (String) s.get("course_name"),
                ((Number) s.get("current_sem")).intValue());
            if (ok) sent++; else failed++;
        }
        if (sent > 0) {
            ra.addFlashAttribute("flash_type", "success");
            ra.addFlashAttribute("flash", "Registration initiated! Notifications sent to " + sent + " student(s).");
        }
        if (failed > 0) {
            ra.addFlashAttribute("flash_type2", "warn");
            ra.addFlashAttribute("flash2", failed + " email(s) could not be delivered.");
        }
        return "redirect:/coordinator";
    }

    /* ── students list ───────────────────────────────────────────── */
    @GetMapping("/students")
    public String students(Authentication auth, Model model,
                            @RequestParam(required = false) String q,
                            @RequestParam(required = false) String course,
                            @RequestParam(required = false) String sem) {
        long coordId = getCoordId(auth.getName());
        model.addAttribute("search", q);
        model.addAttribute("course_filter", course);
        model.addAttribute("sem_filter", sem);

        List<Map<String, Object>> assignedCourses = jdbc.queryForList("""
            SELECT DISTINCT c.course_id, c.short_code, c.course_name
            FROM coordinator_assignments ca JOIN courses c ON ca.course_id=c.course_id
            WHERE ca.coord_id=? ORDER BY c.short_code
            """, coordId);
        model.addAttribute("assigned_courses", assignedCourses);

        List<Integer> assignedSems = jdbc.queryForList(
            "SELECT DISTINCT ca.semester FROM coordinator_assignments ca WHERE ca.coord_id=? ORDER BY ca.semester",
            Integer.class, coordId);
        model.addAttribute("assigned_sems", assignedSems);

        StringBuilder sql = new StringBuilder("""
            SELECT u.full_name, s.roll_no, c.short_code, s.current_sem,
                   r.status, r.reg_id,
                   (SELECT STRING_AGG(sub.subject_name, ', ')
                    FROM registration_subjects rs
                    JOIN subjects sub ON rs.subject_id=sub.subject_id
                    WHERE rs.reg_id=r.reg_id) AS subjects
            FROM students s
            JOIN users u ON s.user_id=u.user_id
            JOIN courses c ON s.course_id=c.course_id
            LEFT JOIN (SELECT student_id, MAX(reg_id) AS reg_id FROM registrations GROUP BY student_id) latest
                ON latest.student_id=s.student_id
            LEFT JOIN registrations r ON r.reg_id=latest.reg_id
            WHERE EXISTS (SELECT 1 FROM coordinator_assignments ca
                          WHERE ca.coord_id=? AND ca.course_id=s.course_id AND ca.semester=s.current_sem)
            """);
        List<Object> params = new ArrayList<>(List.of(coordId));
        if (q != null && !q.isBlank()) {
            sql.append(" AND (u.full_name LIKE ? OR s.roll_no LIKE ?)");
            params.add("%" + q + "%"); params.add("%" + q + "%");
        }
        if (course != null && !course.isBlank()) { sql.append(" AND c.short_code=?"); params.add(course); }
        if (sem != null && !sem.isBlank())        { sql.append(" AND s.current_sem=?"); params.add(Integer.parseInt(sem)); }
        sql.append(" ORDER BY u.full_name");

        model.addAttribute("students", jdbc.queryForList(sql.toString(), params.toArray()));
        return "coordinator_students";
    }

    /* ── Export CSV / Excel ──────────────────────────────────────── */
    @GetMapping("/export")
    public ResponseEntity<?> export(Authentication auth,
                                     @RequestParam(defaultValue = "registered") String type,
                                     @RequestParam(defaultValue = "csv") String fmt,
                                     @RequestParam(required = false) String course,
                                     @RequestParam(required = false) String sem) throws IOException {
        long coordId = getCoordId(auth.getName());

        String[] headers;
        List<Object[]> data = new ArrayList<>();
        String fileBase;

        if ("registered".equals(type)) {
            headers = new String[]{"Full Name","Roll No","Course","semester","Acad Year","status","Submitted At","Fee Receipt","subjects"};
            fileBase = "registered_students";
            StringBuilder sql = new StringBuilder("""
                SELECT u.full_name, s.roll_no, c.short_code AS Course,
                       r.semester, r.acad_year, r.status, r.submitted_at, r.fee_receipt_path,
                       (SELECT STRING_AGG(sub.subject_name, '; ')
                        FROM registration_subjects rs JOIN subjects sub ON rs.subject_id=sub.subject_id
                        WHERE rs.reg_id=r.reg_id) AS subjects
                FROM registrations r
                JOIN students s ON r.student_id=s.student_id
                JOIN users u ON s.user_id=u.user_id
                JOIN courses c ON s.course_id=c.course_id
                WHERE EXISTS (SELECT 1 FROM coordinator_assignments ca
                              WHERE ca.coord_id=? AND ca.course_id=s.course_id AND ca.semester=r.semester)
                """);
            List<Object> params = new ArrayList<>(List.of(coordId));
            if (course != null && !course.isBlank()) { sql.append(" AND c.short_code=?"); params.add(course); }
            if (sem != null && !sem.isBlank())       { sql.append(" AND r.semester=?"); params.add(Integer.parseInt(sem)); }
            sql.append(" ORDER BY u.full_name");
            for (Map<String, Object> r : jdbc.queryForList(sql.toString(), params.toArray())) {
                data.add(new Object[]{
                    r.get("full_name"), r.get("roll_no"), r.get("course"),
                    r.get("semester"), r.get("acad_year"), r.get("status"),
                    r.get("submitted_at") != null ? r.get("submitted_at").toString().substring(0,19) : "",
                    r.get("fee_receipt_path") != null ? "Yes" : "No",
                    r.get("subjects") != null ? r.get("subjects") : ""
                });
            }
        } else {
            headers = new String[]{"Full Name","Roll No","Course","Current semester"};
            fileBase = "unregistered_students";
            List<Map<String, Object>> per = jdbc.queryForList(
                "SELECT acad_year FROM registration_periods WHERE is_open=TRUE ORDER BY created_at DESC");
            String acadYear = per.isEmpty() ? "2024-25" : (String) per.get(0).get("acad_year");
            StringBuilder sql = new StringBuilder("""
                SELECT u.full_name, s.roll_no, c.short_code AS Course, s.current_sem
                FROM students s JOIN users u ON s.user_id=u.user_id JOIN courses c ON s.course_id=c.course_id
                WHERE u.is_active=TRUE
                  AND s.student_id NOT IN (SELECT student_id FROM registrations WHERE acad_year=?)
                  AND EXISTS (SELECT 1 FROM coordinator_assignments ca
                              WHERE ca.coord_id=? AND ca.course_id=s.course_id AND ca.semester=s.current_sem)
                """);
            List<Object> params = new ArrayList<>(List.of(acadYear, coordId));
            if (course != null && !course.isBlank()) { sql.append(" AND c.short_code=?"); params.add(course); }
            if (sem != null && !sem.isBlank())       { sql.append(" AND s.current_sem=?"); params.add(Integer.parseInt(sem)); }
            sql.append(" ORDER BY u.full_name");
            for (Map<String, Object> r : jdbc.queryForList(sql.toString(), params.toArray())) {
                data.add(new Object[]{r.get("full_name"), r.get("roll_no"), r.get("course"), r.get("current_sem")});
            }
        }

        if ("excel".equals(fmt)) {
            try (Workbook wb = new XSSFWorkbook()) {
                Sheet sheet = wb.createSheet(type);
                Row headerRow = sheet.createRow(0);
                for (int i = 0; i < headers.length; i++) headerRow.createCell(i).setCellValue(headers[i]);
                int rowIdx = 1;
                for (Object[] row : data) {
                    Row r = sheet.createRow(rowIdx++);
                    for (int i = 0; i < row.length; i++)
                        r.createCell(i).setCellValue(row[i] != null ? row[i].toString() : "");
                }
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                wb.write(baos);
                return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + fileBase + ".xlsx")
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(baos.toByteArray());
            }
        }

        // CSV
        StringBuilder csv = new StringBuilder();
        csv.append(String.join(",", headers)).append("\n");
        for (Object[] row : data) {
            List<String> cells = new ArrayList<>();
            for (Object cell : row) {
                String val = cell != null ? cell.toString().replace("\"", "\"\"") : "";
                cells.add("\"" + val + "\"");
            }
            csv.append(String.join(",", cells)).append("\n");
        }
        byte[] bytes = csv.toString().getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + fileBase + ".csv")
            .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
            .body(bytes);
    }

    /* ── Verify (pending registrations) ─────────────────────────── */
    @GetMapping("/verify")
    public String verify(Authentication auth, Model model) {
        long coordId = getCoordId(auth.getName());
        List<Map<String, Object>> pending = jdbc.queryForList("""
            SELECT r.reg_id, u.full_name, s.roll_no, c.short_code,
                   r.semester, r.acad_year, r.submitted_at, r.fee_receipt_path,
                   (SELECT STRING_AGG(sub.subject_name, ', ')
                    FROM registration_subjects rs JOIN subjects sub ON rs.subject_id=sub.subject_id
                    WHERE rs.reg_id=r.reg_id) AS subjects
            FROM registrations r
            JOIN students s ON r.student_id=s.student_id
            JOIN users u ON s.user_id=u.user_id
            JOIN courses c ON s.course_id=c.course_id
            WHERE r.status='pending'
              AND EXISTS (SELECT 1 FROM coordinator_assignments ca
                          WHERE ca.coord_id=? AND ca.course_id=s.course_id AND ca.semester=r.semester)
            ORDER BY r.submitted_at
            """, coordId);
        model.addAttribute("pending", pending);
        return "coordinator_verify";
    }

    /* ── View fee receipt file ───────────────────────────────────── */
    @GetMapping("/receipt/{filename:.+}")
    public ResponseEntity<Resource> viewReceipt(@PathVariable String filename) {
        Path filePath = Paths.get(uploadDir).resolve(filename).normalize();
        Resource resource = new FileSystemResource(filePath);
        if (!resource.exists()) return ResponseEntity.notFound().build();
        MediaType mediaType = filename.toLowerCase().endsWith(".pdf")
            ? MediaType.APPLICATION_PDF : MediaType.IMAGE_JPEG;
        return ResponseEntity.ok().contentType(mediaType).body(resource);
    }

    /* ── Approve / Reject action ─────────────────────────────────── */
    @PostMapping("/action")
    public String action(Authentication auth,
                          @RequestParam long reg_id,
                          @RequestParam String action,
                          @RequestParam(required = false) String remarks,
                          RedirectAttributes ra) {
        long coordId = getCoordId(auth.getName());

        // Fetch details for email BEFORE updating
        List<Map<String, Object>> info = jdbc.queryForList("""
            SELECT u.Email, u.full_name, c.course_name, c.short_code,
                   r.semester, r.acad_year,
                   (SELECT STRING_AGG(sub.subject_name, ', ')
                    FROM registration_subjects rs
                    JOIN subjects sub ON rs.subject_id=sub.subject_id
                    WHERE rs.reg_id=r.reg_id) AS subjects,
                   cu.full_name AS CoordName
            FROM registrations r
            JOIN students s ON r.student_id=s.student_id
            JOIN users u ON s.user_id=u.user_id
            JOIN courses c ON s.course_id=c.course_id
            JOIN coordinators co ON co.coord_id=?
            JOIN users cu ON co.user_id=cu.user_id
            WHERE r.reg_id=?
            """, coordId, reg_id);

        jdbc.update("""
            UPDATE registrations
            SET status=?, reviewed_at=NOW(), reviewed_by=?, remarks=?
            WHERE reg_id=?
            """, action, coordId, remarks, reg_id);

        if (!info.isEmpty()) {
            Map<String, Object> r = info.get(0);
            boolean sent = emailService.sendRegistrationDecision(
                (String) r.get("email"), (String) r.get("full_name"), action, reg_id,
                (String) r.get("course_name"), (String) r.get("short_code"),
                ((Number) r.get("semester")).intValue(), (String) r.get("acad_year"),
                (String) r.get("subjects"), remarks, (String) r.get("coordname"));
            ra.addFlashAttribute("flash_type", "success");
            ra.addFlashAttribute("flash", "Registration " + action + ". " +
                (sent ? "Email sent to " + r.get("email") + "." : "(Email delivery failed — check SMTP config.)"));
        } else {
            ra.addFlashAttribute("flash_type", "success");
            ra.addFlashAttribute("flash", "Registration " + action + ".");
        }
        return "redirect:/coordinator/verify";
    }

    /* ── Helpers ─────────────────────────────────────────────────── */
    private long getCoordId(String username) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT co.coord_id FROM coordinators co
            JOIN users u ON co.user_id=u.user_id WHERE u.username=?
            """, username);
        return rows.isEmpty() ? -1L : ((Number) rows.get(0).get("coord_id")).longValue();
    }

    private List<Map<String, Object>> getAssignments(long coordId) {
        if (coordId < 0) return Collections.emptyList();
        return jdbc.queryForList("""
            SELECT ca.course_id, c.short_code, c.course_name, ca.semester
            FROM coordinator_assignments ca JOIN courses c ON ca.course_id=c.course_id
            WHERE ca.coord_id=? ORDER BY c.short_code, ca.semester
            """, coordId);
    }
}
