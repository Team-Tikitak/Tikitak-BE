# 작업 계약: 초대 수락 프로필 media 상태·소유자·purpose 검증

- **관련 이슈**: #191
- **브랜치**: `Fix/#191-invite-profile-media-validation` (develop에서 분기)
- **상태**: 완료 (2026-11-13, PR #195)

## 목표
초대 수락(`POST /api/v1/invitation-links/{token}/accept`)에서 본인 소유가 아니거나, purpose가 `PROFILE_IMAGE`가 아니거나, 상태가 `UPLOADED`가 아닌 media는 400 MEDIA018로 거부한다. `markUsed()`는 호출하지 않는다.
이미 ACTIVE/BANNED인 멤버에게는 media 검증보다 INVITE006/007을 먼저 응답한다.

## 범위: 건드려도 되는 파일
- `src/main/java/kusitms/spin/tikitak/service/team/TeamInvitationService.java`: `resolveProfileMedia`와 `acceptInviteLink`의 검사 순서만 고친다.
- `src/test/java/kusitms/spin/tikitak/service/team/TeamInvitationServiceTest.java`: `@Mock MediaRepository`를 추가하고 케이스를 추가한다.

## 작업
1. `resolveProfileMedia(memberId, mediaPublicId)`
   - `mediaPublicId == null`이면 지금처럼 null을 반환한다.
   - media가 없으면 MEDIA018을 던진다(지금과 같음).
   - 다음 중 하나라도 해당하면 MEDIA018을 던진다. 조건은 `TeamService.java:227-230`과 같다(근거: 원래 설계 `ad2e9d8`, 피드 `FeedService.java:472-479`도 같은 방식으로 에러를 던짐).
     - `!media.getMemberId().equals(memberId)`
     - `purpose != PROFILE_IMAGE`
     - `status != UPLOADED`
   - 모든 검사를 통과했을 때만 `markUsed()`를 호출한다.
2. `acceptInviteLink`: `resolveProfileMedia` 호출을 기존 멤버의 ACTIVE/BANNED 검사(INVITE006/007) 뒤로 옮긴다. 기존 멤버 조회 → ACTIVE/BANNED 검사 → media 해석 → 재가입/신규 저장 순서가 된다. 재가입·신규 저장 로직 자체는 바꾸지 않는다.

## 의도된 동작 변경
진입점은 `POST /api/v1/invitation-links/{token}/accept` 하나다. 상위 catch가 없다(`TeamInvitationController.java:61-71`). 앞선 초대·회원·인원 검사는 통과했다고 가정한다.

| 경우 | 이전 | 이후 |
|---|---|---|
| mediaPublicId 없음(null) | 200, 이미지 없이 가입 | 같음 |
| publicId에 해당하는 media 없음 | 400 MEDIA018 | 같음 |
| 본인·PROFILE_IMAGE·UPLOADED | 200, USED로 바뀌고 연결됨 | 같음 |
| DELETED | 200, USED로 되살아나 연결됨 | 400 MEDIA018, 롤백 |
| PENDING | 200, USED로 바뀜(url null 불일치) | 400 MEDIA018, 롤백 |
| 타인 소유(UPLOADED/USED) | 200, 타인 media 연결 | 400 MEDIA018, 롤백 |
| 본인·purpose 불일치(예: FEED_IMAGE) | 200, 연결됨 | 400 MEDIA018, 롤백 |
| 재가입 시 자기 현재 profileMedia(USED) 재전송 | 200, 같은 media가 DELETED가 된 채 연결됨 | 400 MEDIA018, 롤백 |
| 기존 ACTIVE 멤버 + 존재하지 않거나 잘못된 media | 400 MEDIA018 | 400 INVITE006 (`ErrorCode.java:111`) |
| 기존 BANNED 멤버 + 존재하지 않거나 잘못된 media | 400 MEDIA018 | 403 INVITE007 (`ErrorCode.java:112`) |
| 기존 ACTIVE/BANNED 멤버 + 정상 media | INVITE006/007, `markUsed` 후 롤백 | INVITE006/007, media 조회·락 없음 |

- 근거: 이전 동작은 `TeamInvitationService.java:161-216`의 코드 흐름과 researcher 조사 결과를 따른다. 응답은 `GlobalExceptionHandler.java:31-46`이 정하고, `BusinessException`은 RuntimeException이라 롤백된다(`BusinessException.java:6`). 실행으로 확인하지 않았다. 이번 단위 테스트로 확인한다.
- 다른 진입점(`POST /teams`, `PATCH /teams/{id}/members/me`)은 바뀌지 않는다. 각자의 private 메서드를 쓰므로 공유 메서드가 아니다.

## 완료 조건
- [ ] `./gradlew spotlessCheck`, `./gradlew test` 통과
- [ ] `TeamInvitationServiceTest`에 다음 회귀 테스트를 추가한다. 각 테스트는 MEDIA018을 단언하고, media 상태가 바뀌지 않았는지(`markUsed`가 반영되지 않음)와 `teamMemberRepository.save`가 호출되지 않았는지(`never()`)를 함께 확인한다.
  - DELETED / PENDING / 타인 소유 / purpose 불일치 / 재가입 시 USED media
- [ ] 정상 케이스 테스트: 본인·PROFILE_IMAGE·UPLOADED → USED로 바뀌고 저장되는 TeamMember에 url과 media가 연결된다(captor).
- [ ] 순서 테스트: 기존 ACTIVE 멤버가 media publicId와 함께 요청하면 INVITE006을 응답하고, `mediaRepository.findByPublicIdForUpdate`는 호출되지 않는다(`never()`). BANNED도 INVITE007로 같은 방식으로 확인한다.
- [ ] diff 범위가 위 두 파일과 이 계약 파일뿐이다.

## 커밋 구성
1. `[Fix] 초대 수락 프로필 media 소유자·purpose·상태 검증 및 검사 순서 조정 (#191)`: 서비스 수정과 테스트를 함께 커밋한다. 계약 파일도 이 커밋에 넣는다.

## 하지 말 것
- `TeamService`/`TeamMemberService`의 `resolveProfileMedia`, 디버그 로그, 실패 정책(null 무시)은 바꾸지 않는다. 별도 이슈로 다룬다.
- 공통 헬퍼를 추출하지 않는다.
- `Media` 엔티티(`markUsed` 가드)와 `ErrorCode`는 바꾸지 않는다.
- 재가입·신규 저장 로직은 순서 이동 외에 바꾸지 않는다.
- 범위 밖 리팩터링, 손으로 하는 포맷 정리, 승인 없는 커밋/푸시.

## 남은 리스크
- 클라이언트가 지금 비정상 media(USED 재전송, PENDING)로 초대를 수락하고 있다면 400으로 바뀐다. 클라이언트 동작은 미확인이다.
- 이미 운영 DB에 오염된 행(되살아난 DELETED media, 타인 media 공유)이 있는지는 미확인이다. 이번 범위에서는 정리하지 않는다.

## 참고
- 원래 설계 `ad2e9d8`, 필터 제거 `fb52dbc`
- 결정 기록 `docs/decisions/2026-11-12-media-delete-r2-after-commit.md:39`: "DELETED→다른 상태 전이는 #191 경로뿐"이라는 리스크가 이번 수정으로 사라진다.
- lessons-log: 같은 에러 코드를 내는 분기는 `never()`로 고정한다(2026-11-04 #168).
- 후속 이슈(등록 예정): 팀 생성·프로필 수정 media 실패 정책 통일, 헬퍼 공통화, 디버그 로그 정리
