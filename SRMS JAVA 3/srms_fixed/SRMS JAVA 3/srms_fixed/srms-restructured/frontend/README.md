# SRMS Frontend

Static assets served by Spring Boot's embedded Thymeleaf engine.

## Structure

```
frontend/
└── README.md           ← This file

backend/src/main/resources/
├── templates/          ← Thymeleaf HTML pages
│   ├── index.html      ← Landing / Login page  (renamed from login.html)
│   ├── layout.html     ← Shared page layout
│   ├── fragments/
│   │   └── common.html ← Reusable navbar/footer fragments
│   ├── student_dashboard.html
│   ├── student_register.html
│   ├── student_status.html
│   ├── coordinator_dashboard.html
│   ├── coordinator_students.html
│   ├── coordinator_verify.html
│   ├── admin_dashboard.html
│   ├── admin_users.html
│   ├── admin_edit_user.html
│   ├── admin_courses.html
│   ├── admin_subjects.html
│   ├── forgot_password.html
│   └── reset_password.html
└── static/
    └── css/
        └── srms.css    ← Global stylesheet
```

## Notes
- All pages use Bootstrap 5 via CDN + custom `srms.css`
- Thymeleaf Security dialect is used for role-based UI rendering
- The entry URL `/` redirects to `index.html` (login) when unauthenticated,
  or to the role-specific dashboard when authenticated
