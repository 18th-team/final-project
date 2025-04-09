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





