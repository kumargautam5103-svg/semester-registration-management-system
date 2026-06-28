#!/bin/bash
# SRMS — Linux/Mac startup script
echo "Starting SRMS..."
echo "Make sure your .env file has your Neon PostgreSQL credentials."
mvn spring-boot:run
