-- 로컬 개발 전용 테스트 회원
-- 두 계정의 비밀번호: password

INSERT INTO members (
    email,
    password,
    nickname,
    preferred_region,
    preferred_species,
    role,
    status,
    rating_sum,
    review_count,
    created_at,
    updated_at,
    deleted_at
)
SELECT
    seed.email,
    '{bcrypt}$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG',
    seed.nickname,
    'SEOUL',
    'DOG',
    seed.role,
    'ACTIVE',
    0,
    0,
    UTC_TIMESTAMP(6),
    UTC_TIMESTAMP(6),
    NULL
FROM (
         SELECT
             'local-user@example.com' AS email,
             '로컬일반회원' AS nickname,
             'ROLE_USER' AS role

         UNION ALL

         SELECT
             'local-protector@example.com',
             '로컬보호자',
             'ROLE_PROTECTOR'
     ) AS seed
WHERE NOT EXISTS (
    SELECT 1
    FROM members AS existing
    WHERE existing.email = seed.email
);