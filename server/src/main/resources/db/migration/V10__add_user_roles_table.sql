-- V10__add_user_roles_table.sql
-- Creates table for storing user roles (used for stateless JWT)

CREATE TABLE IF NOT EXISTS user_roles (
    user_id BIGINT NOT NULL,
    role VARCHAR(50) NOT NULL,
    PRIMARY KEY (user_id, role),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- Give every existing user the 'USER' role
INSERT INTO user_roles (user_id, role)
SELECT id, 'USER' FROM users
WHERE NOT EXISTS (SELECT 1 FROM user_roles WHERE user_id = users.id AND role = 'USER');

-- Give admin role to users where is_admin = true
INSERT INTO user_roles (user_id, role)
SELECT id, 'ADMIN' FROM users WHERE is_admin = true
AND NOT EXISTS (SELECT 1 FROM user_roles WHERE user_id = users.id AND role = 'ADMIN');