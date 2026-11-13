# 작업 계약: 초대 수락 시 기존 멤버 상태 검사를 정원 검사보다 먼저 실행

- **관련 이슈**: #193
- **브랜치**: `Fix/#193-invite-capacity-order` (develop에서 분기, `ba16604` 기준)
- **상태**: 완료 (2026-11-13, PR #197)

## 목표
`TeamInvitationService.acceptInviteLink`에서 기존 팀 멤버 조회와 ACTIVE(INVITE006)/BANNED(INVITE007) 검사를 정원 검사(INVITE008)보다 앞으로 옮긴다.
LEFT 멤버 재가입과 신규 가입은 지금처럼 정원 검사를 받는다.

## 범위: 건드려도 되는 파일
- `src/main/java/kusitms/spin/tikitak/service/team/TeamInvitationService.java` — `acceptInviteLink` 안의 검사 순서만 바꾼다
- `src/test/java/kusitms/spin/tikitak/service/team/TeamInvitationServiceTest.java`

## 현재 순서 (근거: `TeamInvitationService.java:137-171`)
초대 조회·유효성(INVITE004/005) → 팀 `findByIdForUpdate`·ACTIVE 확인(INVITE004) → 회원 조회(MEMBER001)·캐릭터 확인(MEMBER002) → **정원 검사(INVITE008, :159-162)** → **기존 멤버 조회·상태 검사(INVITE006/007, :164-171)** → media 해석(MEDIA018) → 재가입/신규 저장

바꾼 뒤: … → MEMBER002 → **기존 멤버 조회·상태 검사** → **정원 검사** → media 해석 → 저장

## 의도된 동작 변경
진입점은 `POST /api/v1/invitation-links/{token}/accept` 하나다(`TeamInvitationController.java:63-69`, 다른 호출부 없음, grep 확인).

| 경우 | 이전 | 이후 |
|---|---|---|
| 정원 초과 + 기존 ACTIVE 멤버 | INVITE008 (400) | **INVITE006 (400)** |
| 정원 초과 + 기존 BANNED 멤버 | INVITE008 (400) | **INVITE007 (403)** |
| 정원 초과 + 기존 LEFT 멤버 | INVITE008 (400) | INVITE008 (400), 변경 없음 |
| 정원 초과 + 신규 | INVITE008 (400) | INVITE008 (400), 변경 없음 |
| 정원 여유 (모든 경우) | 변경 없음 | 변경 없음 |

- 트랜잭션 경계, 롤백 규칙, 락 순서는 바꾸지 않는다. 팀 행 락(`findByIdForUpdate`)을 두 검사보다 먼저 잡으므로, 두 검사의 순서를 바꿔도 동시 가입 시 정원 판단은 그대로다.
- 추가되는 쿼리는 없다. 정원 초과 + ACTIVE/BANNED 경우에는 count 쿼리가 실행되지 않는다(쿼리 하나 감소).

## 완료 조건
- [ ] `acceptInviteLink`에서 `findByMemberIdAndTeamId` 호출과 ACTIVE/BANNED 검사가 `countByTeamIdAndStatus` 호출보다 앞에 있다. 그 밖의 줄은 바뀌지 않는다(diff로 확인)
- [ ] 회귀 테스트: 정원이 찬 상태(count = 100)에서
  - ACTIVE 멤버 → INVITE006, `countByTeamIdAndStatus` `never()`
  - BANNED 멤버 → INVITE007, `countByTeamIdAndStatus` `never()`
  - LEFT 멤버 → INVITE008, `rejoin`되지 않음(status LEFT 유지), media 조회 `never()`
  - 신규 → INVITE008, `save` `never()`, media 조회 `never()`
- [ ] 기존 테스트가 strict stubs에 걸리지 않도록 `stubAcceptPreconditions`의 count stub을 조정한다(ACTIVE/BANNED 경로는 이제 count를 호출하지 않음). 기존 테스트의 검증 내용은 바꾸지 않는다
- [ ] 수정한 파일에 Spotless 적용, `./gradlew spotlessCheck` 통과
- [ ] `./gradlew test` 통과 (DB·쿼리를 바꾸지 않으므로 integrationTest는 필수 아님)

## 커밋 구성
1. `[Fix] 초대 수락 시 기존 멤버 상태 검사를 정원 검사보다 먼저 실행 (#193)` — 서비스 순서 변경 + 회귀 테스트 + 이 계약 파일

## 하지 말 것
- 범위 밖 리팩터링, MEMBER002·media 해석 등 다른 검사 순서 변경
- ErrorCode 메시지·HTTP 상태 변경
- 사람 승인 없이 커밋/푸시

## 남은 리스크
- 클라이언트가 "정원 초과 + 이미 참여 중"에서 INVITE008을 기대하고 분기한 코드가 있으면 안내가 바뀐다. 이번 수정의 의도이며 클라이언트 영향은 미확인.

## 참고
- #191 (PR #195): media 해석을 상태 검사 뒤로 옮긴 작업. 같은 메서드의 순서 테스트(`acceptInviteLinkChecksActiveMemberBeforeMedia` 등)가 있다.
