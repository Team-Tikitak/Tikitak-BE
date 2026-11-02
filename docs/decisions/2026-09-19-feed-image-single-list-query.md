# 이미지 배치 조회는 count/첫 이미지 쿼리 분리 대신 전체 리스트 단일 쿼리

- **날짜**: 2026-09-19
- **관련 이슈**: #153 (Feeds 목록 조회 N+1 쿼리 제거)
- **상태**: 결정됨, 적용 완료

## 상황 (Context)
`FeedService.toListItem()`이 이미지에 대해 실제로 필요로 하는 값은 세 가지다:
- `imageCount` — 전체 이미지 개수
- `thumbnailImageUrl` / `heroPreviewUrl` — `orderIndex` 기준 정렬 후 **첫 번째 이미지 1장의 URL**

`FeedImageRepository`에는 이미 `findFirstActiveByFeedIds`(orderIndex=0인 것만 조회)가 있어서 썸네일/히어로는 이걸로 해결 가능했지만, `imageCount`(전체 개수)는 별도 쿼리가 필요했다.

## 검토한 선택지 (Options considered)
1. **쿼리 2개로 분리**: `countByFeedIds`(집계, `Object[]` — feedId/count) + 기존 `findFirstActiveByFeedIds` 재사용(첫 이미지).
2. **쿼리 1개로 통합**: `findActiveByFeedIds`로 피드별 활성 이미지 전체를 가져온 뒤, `Collectors.groupingBy`로 묶고 Java에서 `size()`(개수)와 `sorted().findFirst()`(첫 이미지)를 둘 다 뽑아냄.

## 결정 (Decision)
2번(전체 리스트 단일 쿼리)을 선택했다.

## 이유 (Rationale)
- `createFeed`/`updateFeed`에서 이미지 개수를 `MAX_IMAGE_COUNT = 10`으로 제한하고 있어서, 피드 하나당 이미지 수가 원천적으로 작다. 페이지 하나(최대 50개 피드)를 기준으로 잡아도 전체 이미지 로우 수가 크지 않다.
- 쿼리를 2개로 나누면 리포지토리 메서드가 늘어나고, 서비스 코드에서도 "카운트 맵"과 "첫 이미지 맵"을 각각 만들어야 해서 오히려 복잡도가 올라간다.
- 이미 있는 `sortedImages(Feed feed)`(단건 컨텍스트용) 헬퍼와 동일한 정렬 로직(`Comparator.comparing(FeedImage::getOrderIndex)`)을 리스트 버전에도 그대로 재사용할 수 있어서, 코드 일관성 측면에서도 유리했다.

## 트레이드오프 / 남은 리스크
- 이미지가 앞으로 크게 늘어나는 기능(예: 무제한 첨부)이 추가되면 이 판단을 재검토해야 한다. 지금은 `MAX_IMAGE_COUNT` 상한이 있다는 전제 하의 결정이다.
- `findFirstActiveByFeedIds`(orderIndex=0만 조회하는 기존 메서드)는 그대로 남겨뒀다 — `NotificationService`에서 이미 쓰고 있어서 그쪽은 건드리지 않았다.
