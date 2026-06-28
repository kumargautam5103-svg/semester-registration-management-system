
DROP TABLE IF EXISTS coordinator_assignments CASCADE;
DROP TABLE IF EXISTS registration_subjects   CASCADE;
DROP TABLE IF EXISTS registrations           CASCADE;
DROP TABLE IF EXISTS registration_periods    CASCADE;
DROP TABLE IF EXISTS subjects                CASCADE;
DROP TABLE IF EXISTS coordinators            CASCADE;
DROP TABLE IF EXISTS students                CASCADE;
DROP TABLE IF EXISTS password_reset_tokens   CASCADE;
DROP TABLE IF EXISTS users                   CASCADE;
DROP TABLE IF EXISTS courses                 CASCADE;


CREATE TABLE courses (
    course_id   SERIAL PRIMARY KEY,
    course_name VARCHAR(100) NOT NULL,
    short_code  VARCHAR(10)  NOT NULL UNIQUE,
    duration    VARCHAR(50)  NOT NULL,
    created_at  TIMESTAMP    DEFAULT NOW()
);


CREATE TABLE users (
    user_id       SERIAL PRIMARY KEY,
    username      VARCHAR(50)  NOT NULL UNIQUE,
    password_hash VARCHAR(256) NOT NULL,
    role          VARCHAR(20)  NOT NULL CHECK (role IN ('student','coordinator','admin')),
    full_name     VARCHAR(100) NOT NULL,
    email         VARCHAR(150),
    is_active     BOOLEAN      DEFAULT TRUE,
    created_at    TIMESTAMP    DEFAULT NOW()
);


CREATE TABLE students (
    student_id  SERIAL PRIMARY KEY,
    user_id     INT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    roll_no     VARCHAR(30) NOT NULL UNIQUE,
    course_id   INT NOT NULL REFERENCES courses(course_id),
    current_sem INT NOT NULL DEFAULT 1,
    batch       VARCHAR(20),
    created_at  TIMESTAMP DEFAULT NOW()
);


CREATE TABLE coordinators (
    coord_id   SERIAL PRIMARY KEY,
    user_id    INT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    department VARCHAR(100),
    created_at TIMESTAMP DEFAULT NOW()
);


CREATE TABLE coordinator_assignments (
    assign_id   SERIAL PRIMARY KEY,
    coord_id    INT NOT NULL REFERENCES coordinators(coord_id) ON DELETE CASCADE,
    course_id   INT NOT NULL REFERENCES courses(course_id)     ON DELETE CASCADE,
    semester    INT NOT NULL,
    assigned_at TIMESTAMP DEFAULT NOW(),
    CONSTRAINT uq_coord_assign UNIQUE (coord_id, course_id, semester)
);


CREATE TABLE subjects (
    subject_id   SERIAL PRIMARY KEY,
    subject_code VARCHAR(20)  NOT NULL UNIQUE,
    subject_name VARCHAR(100) NOT NULL,
    course_id    INT NOT NULL REFERENCES courses(course_id),
    semester     INT NOT NULL,
    is_elective  BOOLEAN  DEFAULT FALSE,
    created_at   TIMESTAMP DEFAULT NOW()
);


CREATE TABLE registration_periods (
    period_id  SERIAL PRIMARY KEY,
    acad_year  VARCHAR(20) NOT NULL,
    is_open    BOOLEAN     NOT NULL DEFAULT FALSE,
    start_date DATE,
    end_date   DATE,
    created_at TIMESTAMP   DEFAULT NOW()
);


CREATE TABLE registrations (
    reg_id           SERIAL PRIMARY KEY,
    student_id       INT NOT NULL REFERENCES students(student_id),
    semester         INT NOT NULL,
    acad_year        VARCHAR(20) NOT NULL,
    status           VARCHAR(20) DEFAULT 'pending' CHECK (status IN ('pending','approved','rejected')),
    fee_receipt_path VARCHAR(500),
    submitted_at     TIMESTAMP DEFAULT NOW(),
    reviewed_at      TIMESTAMP,
    reviewed_by      INT REFERENCES coordinators(coord_id),
    remarks          VARCHAR(500)
);


CREATE TABLE registration_subjects (
    id         SERIAL PRIMARY KEY,
    reg_id     INT NOT NULL REFERENCES registrations(reg_id) ON DELETE CASCADE,
    subject_id INT NOT NULL REFERENCES subjects(subject_id)
);


CREATE TABLE password_reset_tokens (
    token_id   SERIAL PRIMARY KEY,
    user_id    INT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    token      VARCHAR(100) NOT NULL UNIQUE,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX ix_prt_token ON password_reset_tokens(token);
CREATE INDEX ix_reg_periods_created ON registration_periods(created_at DESC);
CREATE INDEX ix_reg_subjects_subject ON registration_subjects(subject_id);



INSERT INTO courses (course_name, short_code, duration) VALUES
('Bachelor of Computer Applications', 'BCA', '3 years / 6 semesters'),
('Master of Computer Applications',   'MCA', '2 years / 4 semesters');

INSERT INTO users (username, password_hash, role, full_name, email) VALUES
('admin',   'pass123', 'admin',       'Administrator',    'admin@university.edu'),
('coord',   'pass123', 'coordinator', 'Dr. Anita Mishra', 'anita@university.edu'),
('student', 'pass123', 'student',     'Rahul Sharma',     'rahul@university.edu'),
('s002',    'pass123', 'student',     'Priya Nair',       'priya@university.edu'),
('s003',    'pass123', 'student',     'Ankit Verma',      'ankit@university.edu'),
('s004',    'pass123', 'student',     'Sneha Pillai',     'sneha@university.edu'),
('s005',    'pass123', 'student',     'Mohit Gupta',      'mohit@university.edu'),
('coord2',  'pass123', 'coordinator', 'Prof. Vikram Sen',  'vikram@university.edu');

INSERT INTO students (user_id, roll_no, course_id, current_sem, batch) VALUES
(3, 'BCA/2022/041', 1, 4, '2022-2025'),
(4, 'MCA/2023/012', 2, 2, '2023-2025'),
(5, 'BCA/2022/055', 1, 4, '2022-2025'),
(6, 'MCA/2023/018', 2, 2, '2023-2025'),
(7, 'BCA/2021/031', 1, 6, '2021-2024');

INSERT INTO coordinators (user_id, department) VALUES
(2, 'Computer Science'),
(8, 'Information Technology');

INSERT INTO coordinator_assignments (coord_id, course_id, semester) VALUES
(1, 1, 3),(1, 1, 4),(1, 1, 5),
(2, 2, 1),(2, 2, 2);

INSERT INTO subjects (subject_code, subject_name, course_id, semester, is_elective) VALUES
('BCA-S1-CP',  'C Programming',        1, 1, FALSE),
('BCA-S1-MA',  'Mathematics I',        1, 1, FALSE),
('BCA-S2-OOP', 'OOP with Java',        1, 2, FALSE),
('BCA-S2-DS',  'Data Structures',      1, 2, FALSE),
('BCA-S3-DS2', 'Advanced DSA',         1, 3, FALSE),
('BCA-S3-DM',  'Discrete Mathematics', 1, 3, FALSE),
('BCA-S4-DB',  'DBMS',                 1, 4, FALSE),
('BCA-S4-OS',  'Operating Systems',    1, 4, FALSE),
('BCA-S4-CN',  'Computer Networks',    1, 4, FALSE),
('BCA-S4-WT',  'Web Technologies',     1, 4, FALSE),
('BCA-S4-AI',  'Elective: AI Basics',  1, 4, TRUE),
('BCA-S5-SE',  'Software Engineering', 1, 5, FALSE),
('BCA-S5-CC',  'Cloud Computing',      1, 5, FALSE),
('BCA-S5-IS',  'Elective: InfoSec',    1, 5, TRUE),
('BCA-S6-PJ',  'Project Work',         1, 6, FALSE),
('BCA-S6-IN',  'Industry Internship',  1, 6, FALSE),
('MCA-S1-AJ',  'Advanced Java',        2, 1, FALSE),
('MCA-S1-DM',  'Discrete Maths',       2, 1, FALSE),
('MCA-S2-ML',  'ML Basics',            2, 2, FALSE),
('MCA-S2-CC',  'Cloud Computing',      2, 2, FALSE),
('MCA-S2-SE',  'Software Engineering', 2, 2, FALSE),
('MCA-S2-AR',  'AR/VR Technologies',   2, 2, TRUE),
('MCA-S3-BD',  'Big Data Analytics',   2, 3, FALSE),
('MCA-S3-CY',  'Cybersecurity',        2, 3, FALSE),
('MCA-S4-PJ',  'Research Project',     2, 4, FALSE);

INSERT INTO registration_periods (acad_year, is_open, start_date, end_date)
VALUES ('2024-25', TRUE, CURRENT_DATE, CURRENT_DATE + INTERVAL '30 days');

INSERT INTO registrations (student_id, semester, acad_year, status) VALUES
(1, 4, '2024-25', 'pending'),
(2, 2, '2024-25', 'pending'),
(3, 4, '2024-25', 'approved'),
(4, 2, '2024-25', 'approved'),
(5, 6, '2024-25', 'rejected');

INSERT INTO registration_subjects (reg_id, subject_id) VALUES
(1, 7),(1, 8),(1, 9),(1,10),
(2,19),(2,20),(2,21),
(3, 7),(3, 8),(3,10),
(4,19),(4,20),(4,21),(4,22),
(5,15),(5,16);
