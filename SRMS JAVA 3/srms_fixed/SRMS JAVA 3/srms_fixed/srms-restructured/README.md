# SRMS — Semester Registration Management System

Spring Boot + Thymeleaf + Neon PostgreSQL

---

## Quick Start (Windows)

### Step 1 — Edit your `.env` file
Open `backend/.env` and fill in your **Neon PostgreSQL** credentials:

```
DATABASE_URL=jdbc:postgresql://ep-xxxx.us-east-1.aws.neon.tech/neondb?sslmode=require&channelBinding=require
DB_USERNAME=neondb_owner
DB_PASSWORD=your_password_here
```

Get these from → [console.neon.tech](https://console.neon.tech) → your project → **Connection string** → switch to **JDBC**.

### Step 2 — Set up the database
Run `database/neon_schema.sql` in the Neon SQL Editor. This creates all tables and inserts demo data.

### Step 3 — Run
```
cd backend
mvn spring-boot:run
```
Or double-click `backend/run.bat`

Open → http://localhost:8080

---

## Demo Credentials

| Role | Username | Password |
|------|----------|----------|
| Admin | `admin` | `pass123` |
| Coordinator | `coord` | `pass123` |
| Student | `student` | `pass123` |

---

## How `.env` loading works

`SrmsApplication.java` reads the `.env` file **before** Spring starts, setting each
`KEY=VALUE` pair as a Java System property. This means Spring's `${DATABASE_URL}`
placeholders resolve correctly without any extra libraries.

The `.env` file must be in the **`backend/`** folder (same folder as `pom.xml`),
which is where Maven runs from.

---

## What was fixed

| Issue | Fix |
|-------|-----|
| `.env` not loaded → `Could not resolve placeholder` crash | `SrmsApplication.java` now loads `.env` before Spring starts |
| `application.properties` failing if env vars missing | Added `:default` fallbacks so app starts even without `.env` |
| PostgreSQL boolean SQL (`is_open=1`, `is_active=1`) | Replaced with `TRUE`/`FALSE` throughout all controllers |
| Java int booleans passed to boolean DB columns | Changed `int open/active` → `boolean open/active` |
| `is_elective != null ? 1 : 0` | Fixed to pass Java boolean directly |
| Spurious `{config,controller,service}` directory | Removed |
| Compiled `target/` in source zip | Removed |
| Inline CSS in auth pages | Replaced with external `srms.css` link |
| Plain background color | University background image + glassmorphism UI |

---

## Prerequisites
- Java 17+
- Maven 3.8+
- A Neon PostgreSQL account (free tier works)
