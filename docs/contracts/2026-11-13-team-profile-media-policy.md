# 작업 계약: 팀 생성·프로필 수정 프로필 media 검증 실패 정책 통일

- **관련 이슈**: #194
- **브랜치**: `Refactor/#194-profile_media_policy` (develop `ba16604`에서 분기)
- **상태**: 승인됨 (2026-11-13)

## 목표
프로필 media를 붙이는 세 경로(팀 생성, 내 프로필 수정, 초대 수락)가 같은 해석 컴포넌트를 쓰고, 검증에 실패하면 모두 400 MEDIA018로 응답한다.
프로필 수정에서 지금 연결된 자기 media를 다시 보내면 "이미지 변경 없음"으로 통과시킨다.
디버그 로그는 정리한다.

## 범위: 건드려도 되는 파일
- 새 파일 `src/main/java/kusitms/spin/tikitak/service/team/TeamProfileMediaResolver.java`
- `src/main/java/kusitms/spin/tikitak/service/team/TeamService.java`
- `src/main/java/kusitms/spin/tikitak/service/team/TeamMemberService.java`
- `src/main/java/kusitms/spin/tikitak/service/team/TeamInvitationService.java`
- 새 파일 `src/test/java/kusitms/spin/tikitak/service/team/TeamProfileMediaResolverTest.java`
- `src/test/java/kusitms/spin/tikitak/service/team/TeamServiceTest.java`, `TeamMemberServiceTest.java`, `TeamInvitationServiceTest.java`
- 새 파일 `docs/decisions/2026-11-13-team-profile-media-fail-policy.md`, `docs/decisions/README.md`(인덱스 한 줄)
- 이 계약 파일

## 작업
1. `TeamProfileMediaResolver` (`@Component`, `@RequiredArgsConstructor`, `MediaRepository` 주입, `@Transactional` 없음 — 호출한 서비스의 트랜잭션에 참여)
   - `Media resolve(Long memberId, UUID mediaPublicId)`: 지금 `TeamInvitationService.java:217-228`과 같은 동작이다.
     - `mediaPublicId == null` → null
     - `findByPublicIdForUpdate`(비관적 락)로 조회하고, 없으면 MEDIA018
     - 소유자 불일치 / purpose ≠ `PROFILE_IMAGE` / status ≠ `UPLOADED` 중 하나라도 해당하면 MEDIA018
     - 통과하면 `markUsed()` 후 반환
   - 이전 media `markDeleted`, rejoin 같은 경로별 처리는 넣지 않는다.
2. `TeamInvitationService`: private `resolveProfileMedia`를 지우고 컴포넌트를 호출한다. 호출 위치(ACTIVE/BANNED 검사 뒤, `TeamInvitationService.java:174`)와 나머지 로직은 그대로다.
3. `TeamService.createTeam`: private `resolveProfileMedia`를 지우고 컴포넌트를 호출한다. 호출 위치(`teamRepository.save`·`changeActiveTeam` 뒤, `TeamService.java:67`)는 옮기지 않는다. 실패하면 트랜잭션 전체가 롤백되므로 결과는 같다.
4. `TeamMemberService.updateMyProfile`
   - private `resolveProfileMedia`를 지우고 컴포넌트를 호출한다.
   - 컴포넌트를 부르기 전에, `teamMember.getProfileMedia()`가 있고 그 `publicId`가 요청 `mediaPublicId`와 같으면 컴포넌트를 부르지 않고 새 media를 null로 둔다("이미지 변경 없음"). 이 경우 기존 media는 `markDeleted`되지 않고 USED로 남는다.
5. 디버그 로그 정리: `TeamService.java:65, 69-71, 218, 221-226`, `TeamMemberService.java:75-76, 80-82, 155, 158-163`을 지운다. 두 클래스에서 `log`를 더 쓰지 않으면 `@Slf4j`와 import도 지운다.
6. 세 서비스에서 `MediaRepository`·`MediaPurpose`·`MediaStatus`를 더 쓰지 않으면 필드와 import를 지운다(남는 사용처가 있으면 둔다).

## 의도된 동작 변경
진입점마다 호출부는 1곳이고, 상위 try/catch는 없다(`TeamController.java:23-31`, `TeamMemberController.java:39-48`, `TeamInvitationController.java:61-71`). 세 메서드 모두 `@Transactional`이고 `BusinessException`은 RuntimeException이라(`BusinessException.java:6`) 예외가 나면 전체 롤백된다. 응답 매핑은 `GlobalExceptionHandler.java:31-46`, MEDIA018은 400(`ErrorCode.java:76`)이다. 근거는 코드 읽기이며, 이번 단위 테스트로 확인한다.

**POST /api/v1/teams** (팀 생성)

| 경우 | 이전 (`TeamService.java:213-234`) | 이후 |
|---|---|---|
| mediaPublicId 없음(null) | 200, 이미지 없이 생성 | 같음 |
| media 없음 | 200, 이미지 null (warn 로그) | 400 MEDIA018, Team·TeamMember·TeamInvite·activeTeam 변경 롤백 |
| 타인 소유 / purpose 불일치 / UPLOADED 아님(PENDING·USED·DELETED) | 200, 이미지 null | 400 MEDIA018, 위와 같이 롤백 |
| 본인·PROFILE_IMAGE·UPLOADED | 200, USED로 바뀌고 연결 | 같음 |

**PATCH /api/v1/teams/{teamId}/members/me** (내 프로필 수정)

| 경우 | 이전 (`TeamMemberService.java:64-97, 150-171`) | 이후 |
|---|---|---|
| mediaPublicId 없음(null) | 200, 닉네임만 변경, 기존 이미지 유지 | 같음 |
| 지금 연결된 자기 profileMedia(USED) 재전송 | 200, 닉네임만 변경, 기존 이미지 유지 (UPLOADED 아님 → null) | 같음. 단, `findByPublicIdForUpdate`(락 조회)를 호출하지 않는다. 현재 media는 LAZY 연관이라 비교할 때 락 없는 SELECT가 1회 나갈 수 있다(`TeamMember.java:52-54`) |
| media 없음 | 200, 닉네임만 변경, 기존 이미지 유지 | 400 MEDIA018, 닉네임 변경도 롤백 |
| 타인 소유 / purpose 불일치 / UPLOADED 아님(현재 media가 아닌 것) | 200, 닉네임만 변경, 기존 이미지 유지 | 400 MEDIA018, 닉네임 변경도 롤백 |
| 본인·PROFILE_IMAGE·UPLOADED | 200, 기존 media DELETED, 새 media USED로 교체 | 같음 |
| 팀 멤버 아님 / ACTIVE 아님 | 404 TEAM_MEMBER001 / 403 TEAM003 (`ErrorCode.java:95, 85`, resolve 전에 검사, `TeamMemberService.java:68-73`) | 같음 |

**POST /api/v1/invitation-links/{token}/accept** (초대 수락): 변경 없음. #191 정책을 컴포넌트로 옮기기만 한다. 재가입 시 자기 이전 profileMedia(USED) 재전송은 지금처럼 400 MEDIA018이다(#191 계약).

## 완료 조건
- [ ] `./gradlew spotlessCheck`, `./gradlew test` 통과 (UP-TO-DATE면 `clean test`)
- [ ] `TeamProfileMediaResolverTest`: null → null이고 조회 안 함(`never()`), 미존재 / 타인 소유 / purpose 불일치 / PENDING / USED / DELETED → MEDIA018이고 media 상태가 바뀌지 않음, 정상 → USED로 바뀌고 같은 객체 반환
- [ ] `TeamServiceTest`: 미존재와 검증 실패(최소 타인 소유 1건) → MEDIA018, `teamMemberRepository.save`·`teamInviteRepository.save`가 호출되지 않음(`never()`). 기존 null·정상 테스트 2개 통과
- [ ] `TeamMemberServiceTest` `updateMyProfile`:
  - 정상 교체 → 기존 media DELETED, 새 media USED, `profileImgUrl`이 새 url로 바뀜. 새 media는 기존과 다른 url로 만든다(lessons-log 2026-11-13 #191)
  - 현재 media 재전송 → 200, 닉네임 변경, 기존 media USED 유지, `findByPublicIdForUpdate` 호출 안 함(`never()`)
  - 미존재 / 검증 실패 → MEDIA018, 닉네임·이미지·기존 media 상태가 바뀌지 않음
  - null → 닉네임만 변경, 조회 안 함
- [ ] `TeamInvitationServiceTest`의 기존 media 테스트 9개가 단언을 바꾸지 않고 통과 (resolver는 mock `MediaRepository`를 넣은 실제 객체를 쓴다)
- [ ] `src/main`의 `service/team` 아래에 `resolveProfileMedia`와 `[createTeam]`·`[updateMyProfile]`·`[resolveProfileMedia]` 로그 문자열이 남아 있지 않다 (grep 0건)
- [ ] 결정 기록 1개 + README 인덱스 한 줄
- [ ] diff 범위가 위 "범위" 파일뿐이다

## 커밋 구성
1. `[Refactor] 프로필 media 해석 공통 컴포넌트 추출 (#194)`: 작업 1·2와 `TeamProfileMediaResolverTest`, `TeamInvitationServiceTest` 조정, 이 계약 파일. 동작 변경 없음.
2. `[Refactor] 팀 생성·프로필 수정 프로필 media 검증 실패를 MEDIA018로 통일 (#194)`: 작업 3~6, `TeamServiceTest`·`TeamMemberServiceTest`, 결정 기록과 인덱스.

## 하지 말 것
- `TeamMemberUpdateRequestDTO.mediaPublicId`를 `PatchField`로 바꾸거나 "명시적 null = 이미지 삭제"를 추가하지 않는다.
- 미존재를 MEDIA009(404)로 분리하지 않는다. `ErrorCode`, `Media` 엔티티, `MediaRepository`는 바꾸지 않는다.
- 팀 생성에서 resolve 호출 위치를 옮기지 않는다. 초대 수락의 검사 순서와 재가입·신규 저장 로직을 바꾸지 않는다.
- 피드·데일리 질문의 media 검증은 건드리지 않는다.
- 범위 밖 리팩터링, 손으로 하는 포맷 정리, 승인 없는 커밋/푸시.

## 남은 리스크
- 앱이 팀 생성·프로필 수정에서 지금 잘못된 media(만료·삭제된 업로드, 다른 purpose 등)를 보내고 있다면 200에서 400으로 바뀐다.
  - 운영 로그에 `[resolveProfileMedia]` 로그(미존재 warn, `found media`·`filter check` info)가 한 번도 찍힌 적이 없다는 사람 확인(2026-11-13). 이 로그는 `mediaPublicId`가 null이 아니면 반드시 찍히므로(`TeamService.java:214-226`, `TeamMemberService.java:151-163`), 운영에서 두 경로에 media ID가 담긴 요청이 resolve까지 온 적이 없다는 뜻이다. 따라서 이번 변경으로 400이 되는 기존 요청은 없을 것으로 본다.
  - 단서: 운영 로그 레벨에서 info가 출력되는지와 로그 보존 기간은 미기록이다. warn(미존재)도 없었으므로 미존재 경로는 레벨과 무관하게 발생하지 않았다.
  - 참고(범위 밖): 앱이 팀 생성·프로필 수정에서 프로필 이미지를 아예 보내지 않거나, 다른 필드명으로 보내고 있을 수 있다. 이번 작업에서는 확인하지 않는다.
- 프로필 수정 실패 시 닉네임 변경도 함께 롤백된다. 지금은 닉네임만 반영되고 200이다.
- 현재 media 재전송 예외는 프로필 수정에만 있다. 초대 재가입의 재전송은 계속 MEDIA018이다(#191 결정 유지).
- 팀 생성 실패 시 Team INSERT 후 롤백되므로 ID 시퀀스에 빈 번호가 생긴다. 동작 영향은 없다.

## 참고
- 원래 설계 `ad2e9d8`(세 경로 모두 MEDIA018), null 무시로 바뀐 커밋 `4312be0`(2026-05-30, 메시지 "로그", 이유 기록 없음)
- #191 계약 `docs/contracts/2026-11-13-invite-profile-media-validation.md`
- 같은 실패 정책: `FeedService.java:472-479`
- lessons-log: 같은 에러 코드 분기는 `never()`로 고정(2026-11-04 #168), media 픽스처 url 중복(2026-11-13 #191)
