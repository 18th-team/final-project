-- 뷰어설정
CREATE OR REPLACE VIEW USER_KEYWORDS_VIEW AS
SELECT
    su.id AS user_id,
    su.name AS user_name,
    STRING_AGG(k.name, ', ') AS keywords
FROM SITE_USER su
         LEFT JOIN USER_KEYWORD uk ON su.id = uk.user_id
         LEFT JOIN KEYWORD k ON uk.keyword_id = k.id
GROUP BY su.id, su.name;

--  뷰어 설정
CREATE OR REPLACE VIEW CLUB_KEYWORDS_VIEW AS
SELECT
    c.id AS club_id,
    c.title AS club_title,
    STRING_AGG(k.name, ', ') AS keywords
FROM CLUB c
         LEFT JOIN CLUB_KEYWORD ck ON c.id = ck.club_id
         LEFT JOIN KEYWORD k ON ck.keyword_id = k.id
GROUP BY c.id, c.title;


-- 초기 카테고리 데이터 삽입 ( merge해서 데이터 있으면 덮어쓰고, 없으면 인서트함)
INSERT INTO keyword (name)
SELECT '액티비티' WHERE NOT EXISTS (SELECT 1 FROM keyword WHERE name = '액티비티');

INSERT INTO keyword (name)
SELECT '자기계발' WHERE NOT EXISTS (SELECT 1 FROM keyword WHERE name = '자기계발');

INSERT INTO keyword (name)
SELECT '취미' WHERE NOT EXISTS (SELECT 1 FROM keyword WHERE name = '취미');

INSERT INTO keyword (name)
SELECT '여행' WHERE NOT EXISTS (SELECT 1 FROM keyword WHERE name = '여행');

INSERT INTO keyword (name)
SELECT '문화/예술' WHERE NOT EXISTS (SELECT 1 FROM keyword WHERE name = '문화/예술');

INSERT INTO keyword (name)
SELECT '푸드/드링크' WHERE NOT EXISTS (SELECT 1 FROM keyword WHERE name = '푸드/드링크');


