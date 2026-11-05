# 피드 목록 N+1을 EntityGraph 확장 대신 배치 조회로 해결

- **날짜**: 2026-09-19
- **관련 이슈**: #153 (Feeds 목록 조회 N+1 쿼리 제거)
- **상태**: 결정됨, 적용 완료

## 상황 (Context)
부하테스트(#151) 결과 `/feeds` 목록 API에서 `feed_image`/`feed_tag` 쿼리가 목록 쿼리 대비 평균 12.7~12.8배 더 호출됐다. 원인은 `FeedRepository`의 목록 조회 메서드(`findActiveFirstPage` 계열, `findActiveByIds`)에 걸린 `@EntityGraph(attributePaths = {"teamMember", "teamMember.member", "place", "question"})`에 `images`/`tags`가 빠져 있어서, `FeedService.toListItem()`이 `feed.getImages()`/`feed.getTags()`를 호출할 때마다 지연 로딩 쿼리가 피드 하나당 추가로 나갔기 때문.

## 검토한 선택지 (Options considered)
1. **EntityGraph에 `images`, `tags`를 추가**해서 한 번의 쿼리로 fetch join.
2. **기존 배치 조회 패턴 재사용**: 이미 `commentCounts(feedIds)`/`reactionSummaries(feedIds)`/`myReactions(feedIds, ...)`에서 쓰던 것처럼, `feedId IN (...)`으로 이미지/태그를 별도 쿼리 1~2개로 한 번에 가져와 Java에서 `Map`으로 묶은 뒤 `toListItem()`에 넘기는 방식.

## 결정 (Decision)
2번(배치 조회 패턴)을 선택했다.

## 이유 (Rationale)
- `@OneToMany` 컬렉션 2개(`images`, `tags`)를 페이지네이션 쿼리의 EntityGraph에 동시에 fetch join하면 SQL 레벨에서 CROSS JOIN처럼 행이 곱해지는 **row multiplication** 문제가 생긴다 — 피드 1개에 이미지 3장, 태그 2명이면 6행이 반환되고, `Pageable`의 `LIMIT`이 "피드 수"가 아니라 "곱해진 행 수" 기준으로 잘려서 페이지네이션 자체가 깨진다. (Hibernate가 컬렉션을 2개 이상 동시에 fetch join하면 `MultipleBagFetchException`을 던지기도 함.)
- 반면 이 코드베이스는 이미 정확히 이 문제(1:N 관계를 페이지네이션 쿼리와 함께 안전하게 가져오기)를 배치 조회로 풀어놓은 전례(`commentCounts`, `reactionSummaries`, `myReactions`)가 있었다. 같은 패턴을 재사용하면 별도의 새로운 개념 도입 없이, 기존 코드 스타일과 일관되게 문제를 해결할 수 있었다.

## 트레이드오프 / 남은 리스크
- 목록 쿼리 1개 + 배치 쿼리 여러 개(comment/reaction/reaction-my/image/tag)로, 쿼리 개수 자체는 여전히 여러 개다. 다만 "피드 수에 비례해서 늘어나던 쿼리"가 "페이지당 고정된 개수의 쿼리"로 바뀐 것이 핵심이라 N+1은 해소된다.
- k6 재실행을 통한 실제 p95 개선폭 확인은 로컬 Postgres/Redis(Docker) 환경이 필요해 이 세션에서는 진행하지 못했고, 별도로 확인이 필요하다.
