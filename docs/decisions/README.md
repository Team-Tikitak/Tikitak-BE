# 결정 기록 (Decision Log)

작업하면서 내린 판단/결정과 그 이유를 남겨두는 곳. 
결정 하나당 파일 하나(`YYYY-MM-DD-슬러그.md`)로 관리하고, 여기서는 목록만 훑어본다.

| 날짜 | 결정 | 관련 이슈 | 한 줄 요약 |
|---|---|---|---|
| 2026-09-19 | [피드 목록 N+1을 EntityGraph 확장 대신 배치 조회로 해결](2026-09-19-feed-n1-batch-fetch-pattern.md) | #153 | 페이지네이션 쿼리에 컬렉션을 EntityGraph로 fetch join하면 row multiplication이 생기므로, 기존 commentCounts류와 같은 `feedId IN (...)` 배치 조회 패턴을 재사용 |
| 2026-09-19 | [이미지 배치 조회는 count/첫 이미지 쿼리 분리 대신 전체 리스트 단일 쿼리](2026-09-19-feed-image-single-list-query.md) | #153 | `MAX_IMAGE_COUNT=10`으로 상한이 있어 전체를 한 번에 가져와 Java에서 개수/정렬/첫 항목을 뽑는 게 쿼리 2개보다 단순 |
| 2026-09-19 | [Map<Long, List<>> + groupingBy를 쓴 이유](2026-09-19-groupingby-for-one-to-many-batch-maps.md) | #153 | 이미지/태그는 피드 하나에 여러 개 붙는 1:N 관계라 toMap이 아니라 groupingBy로 묶어야 함 |
| 2026-09-21 | [MediaUpload 정리는 즉시 삭제 대신 기존 만료 배치에 편입](2026-09-21-mediaupload-cleanup-fold-into-batch.md) | #154 | Media가 나중에 USED가 될 수 있어 완료 시점 즉시 삭제는 위험, 기존 배치에 4번째 단계로 편입 |
| 2026-09-21 | [MediaUpload 삭제 조건은 status 전이 없이 자식 없음+expiresAt만으로 판단](2026-09-21-mediaupload-orphan-check-no-status-transition.md) | #154 | Media.upload는 생성 시 한 번만 세팅되는 단조 관계라 not-exists 체크만으로 안전하게 삭제 가능 |
| 2026-09-21 | [media_upload.expires_at 인덱스는 필터가 아니라 ORDER BY+LIMIT 최적화용](2026-09-21-media-upload-expires-at-index-tradeoff.md) | #154 | 필터 선택도는 낮지만 정리 배치의 ORDER BY+LIMIT 쿼리가 테이블 커질수록 비싸져서 예방적으로 추가 |
| 2026-09-21 | [CREATE INDEX CONCURRENTLY + Flyway 기본 락 설정의 자기 자신 데드락](2026-09-21-flyway-concurrent-index-deadlock.md) | #154 | Flyway가 트랜잭션 안에서 들고 있는 schema-history 락 때문에 CONCURRENTLY가 무한 대기, transactionalLock=false로 해결 |
| 2026-11-02 | [ArchUnit 기존 위반은 동결 대신 명시적 예외 목록으로 허용](2026-11-02-archunit-explicit-exceptions.md) | - | FreezingArchRule store는 무엇을 왜 허용했는지 리뷰에서 보이지 않아, 규칙별 예외 상수에 이유·후속 조치 주석을 달아 관리 |