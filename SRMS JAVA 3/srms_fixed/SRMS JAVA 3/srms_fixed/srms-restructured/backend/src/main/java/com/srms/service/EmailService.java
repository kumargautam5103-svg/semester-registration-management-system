package com.srms.service;

import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * Email helper service — mirrors the three send_*_email() helpers in app.py.
 */
@Service
public class EmailService {

    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Value("${spring.mail.from.address:noreply@srms.edu}")
    private String fromAddress;

    @Value("${spring.mail.from.name:SRMS Portal}")
    private String fromName;

    /* ── Registration Initiated ─────────────────────────────────── */
    public boolean sendRegistrationInitiated(String toEmail, String studentName,
                                              String acadYear, String courseName, int semester) {
        String subject = "[SRMS] Semester Registration Open - " + acadYear;
        String html = """
            <div style="font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;
                        max-width:560px;margin:0 auto;background:#fff;border:1px solid #e0e0e0;
                        border-radius:10px;overflow:hidden;">
              <div style="background:#185FA5;padding:20px 24px;">
                <h2 style="color:#fff;margin:0;font-size:18px;font-weight:500;">
                  SRMS - Semester Registration Portal
                </h2>
              </div>
              <div style="padding:24px;">
                <p style="font-size:15px;color:#111;margin-bottom:12px;">
                  Dear <strong>%s</strong>,
                </p>
                <p style="font-size:14px;color:#444;line-height:1.6;margin-bottom:16px;">
                  Your coordinator has initiated the semester registration for the current academic period.
                  You can now log in to the SRMS portal and submit your registration.
                </p>
                <table style="width:100%%;background:#f5f8fc;border-radius:8px;padding:14px;
                              border-collapse:collapse;margin-bottom:20px;">
                  <tr>
                    <td style="font-size:12px;color:#666;padding:6px 8px;">Academic Year</td>
                    <td style="font-size:13px;color:#111;font-weight:500;padding:6px 8px;">%s</td>
                  </tr>
                  <tr>
                    <td style="font-size:12px;color:#666;padding:6px 8px;">Course</td>
                    <td style="font-size:13px;color:#111;font-weight:500;padding:6px 8px;">%s</td>
                  </tr>
                  <tr>
                    <td style="font-size:12px;color:#666;padding:6px 8px;">Semester</td>
                    <td style="font-size:13px;color:#111;font-weight:500;padding:6px 8px;">%d</td>
                  </tr>
                </table>
                <a href="http://localhost:8080/student/register"
                   style="display:inline-block;background:#185FA5;color:#fff;padding:10px 22px;
                          border-radius:6px;text-decoration:none;font-size:14px;font-weight:500;">
                  Go to Registration Portal
                </a>
              </div>
              <div style="padding:14px 24px;background:#f5f5f5;border-top:1px solid #e0e0e0;">
                <p style="font-size:11px;color:#999;margin:0;">
                  This is an automated message from SRMS Portal. Do not reply.
                </p>
              </div>
            </div>
            """.formatted(studentName, acadYear, courseName, semester);

        return send(toEmail, subject, html);
    }

    /* ── Password Reset ─────────────────────────────────────────── */
    public boolean sendPasswordReset(String toEmail, String fullName, String resetUrl) {
        String subject = "[SRMS] Password Reset Request";
        String html = """
            <div style="font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;
                        max-width:560px;margin:0 auto;background:#fff;border:1px solid #e0e0e0;
                        border-radius:10px;overflow:hidden;">
              <div style="background:#185FA5;padding:20px 24px;">
                <h2 style="color:#fff;margin:0;font-size:18px;font-weight:500;">
                  SRMS - Password Reset
                </h2>
              </div>
              <div style="padding:24px;">
                <p style="font-size:15px;color:#111;margin-bottom:12px;">
                  Dear <strong>%s</strong>,
                </p>
                <p style="font-size:14px;color:#444;line-height:1.6;margin-bottom:20px;">
                  We received a request to reset your SRMS portal password.
                  Click the button below to set a new password.
                  This link expires in <strong>30 minutes</strong>.
                </p>
                <a href="%s"
                   style="display:inline-block;background:#185FA5;color:#fff;padding:10px 22px;
                          border-radius:6px;text-decoration:none;font-size:14px;font-weight:500;">
                  Reset My Password
                </a>
                <p style="font-size:12px;color:#888;margin-top:20px;">
                  If you did not request this, please ignore this email.
                </p>
              </div>
              <div style="padding:14px 24px;background:#f5f5f5;border-top:1px solid #e0e0e0;">
                <p style="font-size:11px;color:#999;margin:0;">
                  This is an automated message from SRMS Portal. Do not reply.
                </p>
              </div>
            </div>
            """.formatted(fullName, resetUrl);

        return send(toEmail, subject, html);
    }

    /* ── Registration Decision (approve / reject) ───────────────── */
    public boolean sendRegistrationDecision(String toEmail, String studentName,
                                             String action, long regId,
                                             String courseName, String shortCode,
                                             int semester, String acadYear,
                                             String subjects, String remarks,
                                             String coordinatorName) {
        boolean approved = "approved".equals(action);
        String statusWord  = approved ? "Approved ✅" : "Rejected ❌";
        String headerColor = approved ? "#1a7f37" : "#c0392b";
        String statusColor = approved ? "#1a7f37" : "#c0392b";
        String statusBg    = approved ? "#eafbea" : "#fbeaea";
        String actionMsg   = approved
            ? "Your semester registration has been <strong>approved</strong>. You are now officially registered for the subjects listed below."
            : "Unfortunately, your semester registration has been <strong>rejected</strong>. Please contact your coordinator for further guidance or re-submit after resolving the issue.";

        StringBuilder subjectsHtml = new StringBuilder();
        if (subjects != null && !subjects.isBlank()) {
            for (String s : subjects.split(",")) {
                if (!s.isBlank())
                    subjectsHtml.append("<li style=\"font-size:13px;color:#333;padding:4px 0;\">")
                                .append(s.trim()).append("</li>");
            }
        } else {
            subjectsHtml.append("<li style=\"font-size:13px;color:#aaa;\">No subjects listed</li>");
        }

        String remarksBlock = (remarks != null && !remarks.isBlank())
            ? """
              <div style="margin-top:16px;padding:12px 14px;background:#fff8e1;
                          border-left:3px solid #f0ad4e;border-radius:6px;">
                <div style="font-size:11px;color:#888;margin-bottom:4px;">Coordinator Remarks</div>
                <div style="font-size:13px;color:#555;">%s</div>
              </div>
              """.formatted(remarks)
            : "";

        String subject = "[SRMS] Registration %s — %s Sem %d (%s)".formatted(
                statusWord, shortCode, semester, acadYear);

        String html = """
            <div style="font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;
                        max-width:580px;margin:0 auto;background:#fff;
                        border:1px solid #e0e0e0;border-radius:10px;overflow:hidden;">
              <div style="background:%s;padding:20px 24px;">
                <h2 style="color:#fff;margin:0;font-size:18px;font-weight:500;">
                  SRMS — Semester Registration Portal
                </h2>
              </div>
              <div style="padding:24px;">
                <p style="font-size:15px;color:#111;margin-bottom:12px;">
                  Dear <strong>%s</strong>,
                </p>
                <p style="font-size:14px;color:#444;line-height:1.7;margin-bottom:20px;">
                  %s
                </p>
                <div style="display:inline-block;background:%s;color:%s;
                            border:1px solid %s;border-radius:20px;
                            padding:6px 18px;font-size:14px;font-weight:600;margin-bottom:20px;">
                  %s
                </div>
                <div style="background:#f5f8fc;border-radius:8px;padding:16px;margin-bottom:16px;">
                  <div style="font-size:11px;color:#888;text-transform:uppercase;
                              letter-spacing:.5px;margin-bottom:10px;">Registration Details</div>
                  <table style="width:100%%;border-collapse:collapse;">
                    <tr>
                      <td style="font-size:12px;color:#666;padding:5px 0;">Registration ID</td>
                      <td style="font-size:13px;color:#111;font-weight:500;padding:5px 0;">#%d</td>
                    </tr>
                    <tr>
                      <td style="font-size:12px;color:#666;padding:5px 0;">Course</td>
                      <td style="font-size:13px;color:#111;font-weight:500;padding:5px 0;">%s (%s)</td>
                    </tr>
                    <tr>
                      <td style="font-size:12px;color:#666;padding:5px 0;">Semester</td>
                      <td style="font-size:13px;color:#111;font-weight:500;padding:5px 0;">Semester %d</td>
                    </tr>
                    <tr>
                      <td style="font-size:12px;color:#666;padding:5px 0;">Academic Year</td>
                      <td style="font-size:13px;color:#111;font-weight:500;padding:5px 0;">%s</td>
                    </tr>
                    <tr>
                      <td style="font-size:12px;color:#666;padding:5px 0;">Reviewed By</td>
                      <td style="font-size:13px;color:#111;font-weight:500;padding:5px 0;">%s</td>
                    </tr>
                  </table>
                </div>
                <div style="background:#f9f9f9;border-radius:8px;padding:16px;margin-bottom:16px;">
                  <div style="font-size:11px;color:#888;text-transform:uppercase;
                              letter-spacing:.5px;margin-bottom:8px;">Registered Subjects</div>
                  <ul style="margin:0;padding-left:18px;">
                    %s
                  </ul>
                </div>
                %s
                <a href="http://localhost:8080/student/status"
                   style="display:inline-block;background:#185FA5;color:#fff;padding:10px 22px;
                          border-radius:6px;text-decoration:none;font-size:14px;font-weight:500;margin-top:12px;">
                  View Registration Status
                </a>
              </div>
              <div style="padding:14px 24px;background:#f5f5f5;border-top:1px solid #e0e0e0;">
                <p style="font-size:11px;color:#999;margin:0;">
                  This is an automated message from SRMS Portal. Do not reply.
                </p>
              </div>
            </div>
            """.formatted(
                headerColor, studentName, actionMsg,
                statusBg, statusColor, statusColor, statusWord,
                regId, courseName, shortCode, semester, acadYear, coordinatorName,
                subjectsHtml, remarksBlock);

        return send(toEmail, subject, html);
    }

    /* ── Internal send helper ───────────────────────────────────── */
    private boolean send(String to, String subject, String html) {
        if (mailSender == null) {
            System.err.println("[MAIL] JavaMailSender not configured. Skipping email to " + to);
            return false;
        }
        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, true, "UTF-8");
            helper.setFrom(fromAddress, fromName);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(msg);
            return true;
        } catch (Exception e) {
            System.err.println("[MAIL ERROR] Failed to send to " + to + ": " + e.getMessage());
            return false;
        }
    }
}
