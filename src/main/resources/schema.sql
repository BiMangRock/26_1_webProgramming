CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL
);

CREATE TABLE IF NOT EXISTS media (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    file_type VARCHAR(50) NOT NULL,    -- 코틀린 Entity의 fileType과 자동 매핑
    file_name VARCHAR(255) NOT NULL,   -- 코틀린 Entity의 fileName과 자동 매핑
    content_type VARCHAR(100) NOT NULL -- 코틀린 Entity의 contentType과 자동 매핑
);