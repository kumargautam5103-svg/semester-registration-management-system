package com.srms.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api")
public class ApiController {

    @Autowired private JdbcTemplate jdbc;

    @GetMapping("/subjects/{courseId}/{semester}")
    public List<Map<String, Object>> subjects(@PathVariable long courseId, @PathVariable int semester) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT subject_id, subject_name, is_elective
            FROM subjects WHERE course_id=? AND semester=?
            ORDER BY is_elective, subject_name
            """, courseId, semester);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.get("subject_id"));
            m.put("name", r.get("subject_name"));
            m.put("elective", Boolean.TRUE.equals(r.get("is_elective")) || Integer.valueOf(1).equals(r.get("is_elective")));
            result.add(m);
        }
        return result;
    }

    @GetMapping("/course-semesters/{courseId}")
    public List<Integer> courseSemesters(@PathVariable long courseId) {
        return jdbc.queryForList(
            "SELECT DISTINCT semester FROM subjects WHERE course_id=? ORDER BY semester",
            Integer.class, courseId);
    }

    @GetMapping("/courses")
    public List<Map<String, Object>> courses() {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT course_id, short_code, course_name, Duration FROM courses ORDER BY short_code");
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.get("course_id"));
            m.put("code", r.get("short_code"));
            m.put("name", r.get("course_name"));
            m.put("duration", r.get("duration"));
            result.add(m);
        }
        return result;
    }

    @GetMapping("/coordinator-semesters/{courseId}")
    public List<Integer> coordinatorSemesters(@PathVariable long courseId) {
        return jdbc.queryForList(
            "SELECT DISTINCT semester FROM subjects WHERE course_id=? ORDER BY semester",
            Integer.class, courseId);
    }
}
