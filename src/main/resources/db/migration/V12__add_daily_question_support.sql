ALTER TABLE question
    ADD COLUMN question_order INTEGER,
    ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN effective_from DATE,
    ADD COLUMN updated_at TIMESTAMP;

UPDATE question
SET updated_at = created_at
WHERE updated_at IS NULL;

ALTER TABLE question
    ALTER COLUMN updated_at SET NOT NULL;

CREATE UNIQUE INDEX uk_question_order
    ON question(question_order);

CREATE INDEX idx_question_active_effective_order
    ON question(active, effective_from, question_order);

ALTER TABLE feed
    ADD COLUMN question_answer_date DATE,
    ADD COLUMN daily_answer_active_key VARCHAR(100);

CREATE UNIQUE INDEX uk_feed_daily_answer_active_key
    ON feed(daily_answer_active_key);

CREATE INDEX idx_feed_daily_answer_lookup
    ON feed(team_id, team_member_id, question_id, question_answer_date, deleted_at);

INSERT INTO question (content, date, question_order, active, effective_from, created_at, updated_at)
VALUES
    ('오늘 OOTD에서 가장 마음에 드는 포인트는 무엇인가요?', NULL, 1, TRUE, DATE '2025-01-01', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('이번 주에 산 것 중 가장 만족스러운 물건은 무엇인가요?', NULL, 2, TRUE, DATE '2025-01-01', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('오늘 혼자 보기 아까웠던 웃긴 장면은 무엇인가요?', NULL, 3, TRUE, DATE '2025-01-01', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('오늘 유독 눈에 들어온 색은 무엇인가요?', NULL, 4, TRUE, DATE '2025-01-01', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('오늘의 나를 가장 잘 보여주는 물건은 무엇인가요?', NULL, 5, TRUE, DATE '2025-01-01', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('오늘 발견한 가장 귀여운 순간은 무엇인가요?', NULL, 6, TRUE, DATE '2025-01-01', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('요즘 자주 찾는 최애 간식은 무엇인가요?', NULL, 7, TRUE, DATE '2025-01-01', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('오늘 나를 위한 작은 사치는 무엇인가요?', NULL, 8, TRUE, DATE '2025-01-01', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('팀원들이 보면 궁금해할 만한 물건은 무엇인가요?', NULL, 9, TRUE, DATE '2025-01-01', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('오늘 괜히 자랑하고 싶은 것은 무엇인가요?', NULL, 10, TRUE, DATE '2025-01-01', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('요즘 가장 자주 듣는 노래는 무엇인가요?', NULL, 11, TRUE, DATE '2025-01-01', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('팀원들에게 추천하고 싶은 콘텐츠는 무엇인가요?', NULL, 12, TRUE, DATE '2025-01-01', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('요즘 나를 웃게 하는 것은 무엇인가요?', NULL, 13, TRUE, DATE '2025-01-01', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('요즘 알고리즘이 자주 보여주는 것은 무엇인가요?', NULL, 14, TRUE, DATE '2025-01-01', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
