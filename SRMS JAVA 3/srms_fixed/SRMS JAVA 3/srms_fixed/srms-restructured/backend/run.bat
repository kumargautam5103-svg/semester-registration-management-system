@echo off
REM ============================================================
REM SRMS — Windows startup script
REM Run this from: backend\ folder
REM ============================================================
echo Starting SRMS...
echo.
echo Make sure your .env file has your Neon PostgreSQL credentials.
echo See .env.example for the format.
echo.
mvn spring-boot:run
pause
