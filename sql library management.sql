DROP DATABASE IF EXISTS library_db;
CREATE DATABASE library_db;
USE library_db;
CREATE TABLE users(user_id INT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(100) NOT NULL, email VARCHAR(150) NOT NULL UNIQUE, role VARCHAR(30) NOT NULL, password_hash VARCHAR(255) NOT NULL, contact VARCHAR(20) NOT NULL, status VARCHAR(20) NOT NULL DEFAULT 'APPROVED');

CREATE TABLE books(isbn VARCHAR(20) PRIMARY KEY, title VARCHAR(200) NOT NULL, author VARCHAR(150) NOT NULL, type VARCHAR(50) NOT NULL, total_copies INT NOT NULL, available_copies INT NOT NULL, shelf_location VARCHAR(50), 
CHECK (total_copies>=0), CHECK (available_copies>=0), CHECK (available_copies <= total_copies));

CREATE TABLE transactions(txn_id INT AUTO_INCREMENT PRIMARY KEY, user_id INT NOT NULL, isbn VARCHAR(20) NOT NULL, issue_date DATE NOT NULL, due_date DATE NOT NULL, return_date DATE, status VARCHAR(30) NOT NULL, FOREIGN KEY (user_id) REFERENCES users(user_id), FOREIGN KEY (isbn) REFERENCES books(isbn));

CREATE TABLE fines(fine_id INT AUTO_INCREMENT PRIMARY KEY, txn_id INT NOT NULL, 
amount DECIMAL(10,2) NOT NULL DEFAULT 0.00, paid_status BOOLEAN NOT NULL DEFAULT FALSE,
FOREIGN KEY (txn_id) REFERENCES transactions(txn_id), CHECK (amount>=0));

CREATE TABLE waitlist(isbn VARCHAR(20) NOT NULL, title VARCHAR(200), user_id INT NOT NULL, request_date DATETIME(6) NOT NULL, 
status VARCHAR(20) NOT NULL DEFAULT 'WAITING',
PRIMARY KEY(isbn, user_id), FOREIGN KEY(user_id) REFERENCES users(user_id) ON DELETE CASCADE);

CREATE TABLE notifications(notification_id INT AUTO_INCREMENT PRIMARY KEY, user_id INT NOT NULL, 
message VARCHAR(255) NOT NULL, is_read BOOLEAN NOT NULL DEFAULT FALSE, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, 
FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE);

INSERT INTO users
    (name, email, role, password_hash, contact, status)
VALUES
    ('Ananya', 'ananya@example.com', 'STUDENT', '$2a$10$/qTzR0RBdPwlbr4hCiEEQ.nuC7PeUt.Pd7zyTgXFAjrmX/pPPOS3C', '9876543210', 'APPROVED'),
    ('Rahul', 'rahul@example.com', 'STUDENT', '$2a$10$/qTzR0RBdPwlbr4hCiEEQ.nuC7PeUt.Pd7zyTgXFAjrmX/pPPOS3C', '9876543211', 'APPROVED'),
    ('Admin', 'admin@example.com', 'ADMIN', '$2a$10$FgZDV5yI444hhyuNhTKE/./vUG.tVQhecbgyiwUzOx71SJpGgjKhy', '9876543212', 'APPROVED');
-- Note: Default password for Student accounts is 'pass123', and for Admin is 'admin'

INSERT INTO books
    (isbn, title, author, type, total_copies,
     available_copies, shelf_location)
VALUES
    ('9780134685991', 'Effective Java', 'Joshua Bloch',
     'TEXT BOOK', 5, 5, 'A1'),
    ('9780132350884', 'Clean Code', 'Robert C. Martin',
     'TEXT BOOK', 3, 2, 'A2'),
    ('9780596007126', 'Head First Design Patterns',
     'Eric Freeman', 'REFERENCE BOOK', 4, 4, 'B1');
-- (issue_date is already correctly named in CREATE TABLE transactions)

INSERT INTO transactions
    (user_id, isbn, issue_date, due_date, return_date, status)
VALUES
    (1, '9780132350884', '2026-10-01', '2026-10-15',
     NULL, 'ISSUED');

INSERT INTO fines (txn_id, amount, paid_status)
VALUES (1, 0.00, FALSE);

INSERT INTO waitlist (isbn, user_id, request_date)
VALUES ('9780132350884', 2, '2026-10-11');

SELECT * FROM users;
SELECT * FROM books;
SELECT * FROM transactions;
SELECT * FROM fines;
SELECT * FROM waitlist;
