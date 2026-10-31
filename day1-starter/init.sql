-- Day 1：只验证 pgvector 能装上。
-- 真正的表结构（documents / chunks / embeddings）在 W3 再设计。
CREATE
EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS healthcheck
(
    id
    serial
    PRIMARY
    KEY,
    note
    text,
    embedding
    vector
(
    3
)
    );

INSERT INTO healthcheck (note, embedding)
VALUES ('pgvector is ready', '[0.1, 0.2, 0.3]') ON CONFLICT DO NOTHING;
