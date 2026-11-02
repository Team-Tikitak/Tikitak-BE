# Map<Long, List<>> + groupingBy를 쓴 이유

- **날짜**: 2026-09-19
- **관련 이슈**: #153 (Feeds 목록 조회 N+1 쿼리 제거)
- **상태**: 결정됨, 적용 완료

## 상황 (Context)
기존 배치 조회 헬퍼(`commentCounts`, `reactionSummaries`, `myReactions`)는 전부 `Map<Long, Long>` / `Map<Long, DTO>`처럼 "피드ID → 값 하나"로 리턴한다. 이번에 추가한 `feedImages`/`feedTags`도 처음엔 같은 모양으로 가야 하나 싶었는데, 실제로는 `Map<Long, List<FeedImage>>` / `Map<Long, List<FeedTag>>`로 만들었다.

## 결정 (Decision)
`Collectors.toMap`이 아니라 `Collectors.groupingBy`를 써서 `Map<Long, List<T>>` 형태로 배치 조회 결과를 묶었다.

```java
private Map<Long, List<FeedImage>> feedImages(List<Long> feedIds) {
    if (feedIds.isEmpty()) {
        return Map.of();
    }
    return feedImageRepository.findActiveByFeedIds(feedIds).stream()
            .collect(Collectors.groupingBy(image -> image.getFeed().getId()));
}
```

## 이유 (Rationale)
- `commentCounts`/`myReactions`/`reactionSummaries`가 다루는 값(댓글 수, 내 반응, 리액션 요약)은 피드 하나당 원래부터 **스칼라 하나**다. 그래서 "피드ID → 값 하나"로 자연스럽게 떨어지고, `toMap`으로 바로 매핑할 수 있다.
- `FeedImage`/`FeedTag`는 엔티티 매핑 자체가 `@OneToMany`다. 피드 하나에 이미지 여러 장, 태그된 멤버 여러 명이 붙을 수 있고, `toListItem()`에서 필요한 것도 하나가 아니다(개수/첫 이미지/전체 태그 목록). "피드ID → 값 하나"로 미리 접어버리면 이 세 가지를 하나의 스칼라로 표현할 수 없다.
- `toMap`은 같은 키가 두 번 들어오면 `IllegalStateException`을 던진다 — "키 하나 = 값 하나"가 전제이기 때문. 반면 `groupingBy`는 같은 키를 자동으로 리스트에 모아준다. 이미지/태그처럼 한 피드에 여러 개가 붙는 게 정상인 1:N 관계에는 `groupingBy`가 맞는 도구다.
- 원본 컬렉션 형태(리스트) 그대로 배치 조회해서 그루핑한 뒤, 필요한 형태(개수/첫 항목/매핑된 리스트)는 `toListItem()` 안에서 그때그때 파생시키는 방식을 택했다.

## 참고
`groupingBy(f)`는 `groupingBy(f, Collectors.toList())`의 축약형이다. 두 번째 인자를 생략하면 묶인 값들을 기본적으로 `List`에 담는다.
