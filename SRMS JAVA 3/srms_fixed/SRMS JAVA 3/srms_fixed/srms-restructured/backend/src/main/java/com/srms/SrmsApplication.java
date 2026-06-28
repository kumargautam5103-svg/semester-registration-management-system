package com.srms;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.File;
import java.io.FileInputStream;
import java.util.Properties;

@SpringBootApplication
public class SrmsApplication {

    public static void main(String[] args) {
        // Load .env file into system properties BEFORE Spring reads application.properties
        loadDotEnv();
        SpringApplication.run(SrmsApplication.class, args);
    }

    /**
     * Reads a .env file (from project root or current directory) and sets
     * each key=value pair as a System property so Spring ${VAR} placeholders resolve.
     * Skips blank lines and comments (#).
     */
    private static void loadDotEnv() {
        // Look for .env relative to current working directory (where mvn is run from)
        File envFile = new File(".env");
        if (!envFile.exists()) {
            // Also try parent directory
            envFile = new File("../.env");
        }
        if (!envFile.exists()) {
            System.out.println("[SRMS] No .env file found — using system env vars or defaults.");
            return;
        }
        try (FileInputStream fis = new FileInputStream(envFile)) {
            Properties props = new Properties();
            // Read line-by-line to handle KEY=VALUE (Properties.load handles = separator)
            java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(fis));
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                int eq = line.indexOf('=');
                if (eq <= 0) continue;
                String key   = line.substring(0, eq).trim();
                String value = line.substring(eq + 1).trim();
                // Remove surrounding quotes if present
                if (value.startsWith("\"") && value.endsWith("\""))
                    value = value.substring(1, value.length() - 1);
                if (value.startsWith("'") && value.endsWith("'"))
                    value = value.substring(1, value.length() - 1);
                // Only set if not already defined by a real system env var
                if (System.getenv(key) == null) {
                    System.setProperty(key, value);
                }
            }
            System.out.println("[SRMS] Loaded .env from: " + envFile.getAbsolutePath());
        } catch (Exception e) {
            System.err.println("[SRMS] Warning: could not read .env — " + e.getMessage());
        }
    }
}
