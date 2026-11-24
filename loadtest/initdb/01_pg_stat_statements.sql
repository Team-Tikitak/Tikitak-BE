-- 볼륨이 비어 있을 때(최초 기동, down -v 직후) 한 번만 실행된다.
-- shared_preload_libraries=pg_stat_statements 는 docker-compose.yml command 에서 켠다.
CREATE EXTENSION IF NOT EXISTS pg_stat_statements;
