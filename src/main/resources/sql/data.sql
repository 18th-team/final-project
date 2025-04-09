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