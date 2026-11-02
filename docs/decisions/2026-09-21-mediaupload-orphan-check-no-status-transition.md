# MediaUpload 삭제 조건은 status 전이 없이 "자식 없음 + expiresAt 경과"만으로 판단

- **날짜**: 2026-09-21
- **관련 이슈**: #154 (MediaUpload 레코드 정리 로직 부재)
- **상태**: 결정됨, 적용 완료

## 상황 (Context)
`MediaUploadStatus`는 `PENDING/COMPLETED/FAILED/EXPIRED`인데, `completeUpload()`가 호출된 적 없는 업로드는 `expiresAt`이 지나도 `PENDING`에 영원히 머문다(전이를 트리거하는 코드 경로가 `completeUpload()` 안의 반응형 체크뿐이라서). `MediaUpload.expiresAt`은 생성 시 `createdAt+7일`로 세팅되지만 어떤 쿼리에서도 조건으로 쓰인 적이 없는 "죽은 필드"였다.

## 검토한 선택지 (Options considered)
1. 배치에 "만료된 `PENDING` upload를 `EXPIRED`로 전이"하는 단계를 먼저 추가하고, 그 다음 `EXPIRED`/`COMPLETED` 상태이면서 자식이 없는 것만 삭제.
2. `status`를 아예 보지 않고, `expiresAt < now AND 자식 Media 없음`이라는 조건만으로 삭제 대상을 판단.

## 결정 (Decision)
2번(status 무시, orphan-check만 사용)을 선택했다.

## 이유 (Rationale)
- `Media.upload`는 생성 시(`createUploadUrls`) 딱 한 번만 세팅되고 이후 재할당하는 코드 경로가 없다 — 즉 "자식이 있다가 없어지는" 방향으로만 단조 변화하고, 없던 자식이 나중에 다시 생기는 일이 없다. 이 단조성 덕분에 `not exists` 체크 하나로 안전하게 삭제 가능 여부를 판단할 수 있어, 별도 상태 전이 단계 없이도 정확하다.
- `Media`가 `USED`(피드에 실제 첨부됨)가 되면 `not exists`가 자연히 `false`가 돼서 삭제되지 않는다 — `status`가 `PENDING`이든 `COMPLETED`든 상관없이 올바르게 동작한다. 반대로 상태 전이 방식을 썼다면 `FAILED`처럼 현재 코드에서 아무도 세팅하지 않는 상태까지 신경 써야 하는 등 케이스가 늘어난다.
- 죽은 필드였던 `expiresAt`을 실제 삭제 조건으로 되살려 쓰게 되는 효과도 있다.

## 트레이드오프 / 남은 리스크
- `MediaUpload.status`는 row가 삭제되기 직전까지 계속 `PENDING`으로 남아있어, DB를 직접 열어보면 "정리가 안 되고 있다"는 착각을 줄 수 있다(실제로는 자식 `Media`의 소프트삭제→하드삭제 체인을 기다리는 중). 상태값의 정확성보다 row 자체를 지우는 게 이슈의 본질이라고 보고 감수하기로 함.
