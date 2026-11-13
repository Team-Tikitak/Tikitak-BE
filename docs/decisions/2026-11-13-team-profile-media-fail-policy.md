# 팀 프로필 media 검증 실패는 세 경로 모두 400 MEDIA018로 응답한다

- **날짜**: 2026-11-13
- **관련 이슈**: #194
- **상태**: 결정됨 (2026-11-13 계약 승인)
- **계약**: [2026-11-13-team-profile-media-policy](../contracts/2026-11-13-team-profile-media-policy.md)

## 상황 (Context)
- 팀 프로필 이미지로 media를 붙이는 경로는 셋이다: 팀 생성(`POST /api/v1/teams`), 내 프로필 수정(`PATCH /api/v1/teams/{teamId}/members/me`), 초대 수락(`POST /api/v1/invitation-links/{token}/accept`). 세 서비스가 각자 private `resolveProfileMedia`를 갖고 있었다.
- 원래 설계(`ad2e9d8`, #86)는 세 경로 모두 "media 없음 / 타인 소유 / purpose ≠ `PROFILE_IMAGE` / status ≠ `UPLOADED`"이면 MEDIA018을 던졌다.
- 같은 날 커밋 메시지가 모두 "로그"인 두 커밋에서 바뀌었고, 둘 다 이유 기록이 없다.
  - `44d3b75`(2026-05-30): 프로필 수정(`TeamMemberService`)에 디버그 로그가 붙었다. 이 시점에는 아직 MEDIA018을 던졌다.
  - `4312be0`(2026-05-30): 팀 생성·프로필 수정 두 서비스가 예외 대신 null을 반환하도록 바뀌었고, 팀 생성(`TeamService`)에 디버그 로그가 붙었다. 그 뒤로 두 경로는 잘못된 media를 보내도 200이고 이미지만 조용히 빠졌다. 프로필 수정은 닉네임만 반영됐다.
- 초대 수락은 #191에서 MEDIA018로 다시 막았다. 피드 생성도 같은 조건에서 MEDIA018을 던진다(`FeedService.validateCreateMedia`).

## 검토한 선택지 (Options considered)
1. **세 경로 모두 MEDIA018, 프로필 수정에서만 현재 media 재전송은 "변경 없음"으로 통과** (채택)
2. (기각) **팀 생성·프로필 수정의 null 무시 유지**
3. (기각) **미존재만 MEDIA009(404)로 분리**
4. (기각) **프로필 수정에서 현재 media 재전송도 MEDIA018**

## 결정 (Decision)
1번. 검증은 공통 컴포넌트 `TeamProfileMediaResolver`(`@Component`, 트랜잭션은 호출한 서비스에 참여)로 모은다.
- `mediaPublicId == null` → null (이미지 변경 없음)
- `findByPublicIdForUpdate`로 조회, 없거나 타인 소유 / purpose ≠ `PROFILE_IMAGE` / status ≠ `UPLOADED`면 MEDIA018
- 통과하면 `markUsed()` 후 반환

프로필 수정은 컴포넌트를 부르기 전에, 지금 연결된 `profileMedia`의 `publicId`가 요청 `mediaPublicId`와 같으면 컴포넌트를 부르지 않고 "이미지 변경 없음"으로 처리한다(기존 media는 USED 유지). 이때 `findByPublicIdForUpdate`(락 조회)는 호출하지 않는다. 현재 media는 LAZY 연관이라 비교할 때 락 없는 SELECT가 1회 나갈 수 있다(`TeamMember.java:52-54`). 요청 `mediaPublicId`가 null이면 비교도 하지 않는다. 경로별 처리(기존 media `markDeleted`, 재가입)는 각 서비스에 남긴다. 디버그 로그(`[createTeam]`, `[updateMyProfile]`, `[resolveProfileMedia]`)는 지운다.

## 이유 (Rationale)
- **2번 기각**: 실패해도 200이라 클라이언트가 이미지가 빠진 것을 알 수 없고, 같은 조건에서 초대 수락(#191)·피드 생성은 MEDIA018이라 경로마다 규칙이 달랐다. null 무시로 바꾼 `4312be0`에는 이유 기록이 없어, 원래 설계(`ad2e9d8`)로 되돌리는 쪽을 택했다.
- **3번 기각**: 피드 생성은 미존재도 MEDIA018로 묶고(`FeedService.validateCreateMedia`의 `media == null`), 초대 수락도 미존재를 MEDIA018로 응답한다(#191). 미존재만 404로 나누면 이 일관성이 깨진다. 클라이언트가 미존재와 다른 검증 실패를 구분해서 처리해야 하는 요구는 확인된 것이 없다(미확인). `ErrorCode`를 바꾸지 않는다는 계약 범위도 이유다.
- **4번 기각**: 이전에는 지금 연결된 자기 media(USED)를 다시 보내도 200이고 기존 이미지가 유지됐다(UPLOADED가 아니라 null 처리). MEDIA018로 바꾸면 이 요청이 200에서 400이 된다. 피드 수정도 현재 붙어 있는 USED media 재전송은 허용한다(`FeedService.validateUpdateMedia`). 앱이 실제로 현재 media를 재전송하는지는 미확인이다.
- 초대 수락은 이 예외를 두지 않는다. 재가입 시 자기 이전 profileMedia(USED) 재전송은 #191 결정대로 MEDIA018이다.

## 의도된 동작 변경
계약의 ["의도된 동작 변경"](../contracts/2026-11-13-team-profile-media-policy.md#의도된-동작-변경)을 따른다. 요약:

| 경로 | 이전 | 이후 |
|---|---|---|
| 팀 생성, media 없음 / 타인 소유 / purpose 불일치 / UPLOADED 아님 | 200, 이미지 null | 400 MEDIA018, Team·TeamMember·TeamInvite·activeTeam 변경 롤백 |
| 프로필 수정, media 없음 / 검증 실패(현재 media가 아닌 것) | 200, 닉네임만 변경 | 400 MEDIA018, 닉네임 변경도 롤백 |
| 프로필 수정, 현재 연결된 media 재전송 | 200, 닉네임만 변경 | 같음. `findByPublicIdForUpdate`(락 조회)를 호출하지 않음. 현재 media는 LAZY 연관이라 비교할 때 락 없는 SELECT가 1회 나갈 수 있음(`TeamMember.java:52-54`) |
| null / 정상 media | - | 같음 |
| 초대 수락 | - | 변경 없음 (#191 정책을 컴포넌트로 옮기기만 함) |

세 메서드 모두 `@Transactional`이고 상위 try/catch가 없어 `BusinessException`이 나면 전체가 롤백된다(근거는 계약의 코드 인용, 단위 테스트로 MEDIA018과 저장·상태 불변을 확인).

## 트레이드오프 / 남은 리스크
- 앱이 팀 생성·프로필 수정에서 지금 잘못된 media(만료·삭제된 업로드, 다른 purpose 등)를 보내고 있다면 200에서 400으로 바뀐다.
  - 운영 로그에 `[resolveProfileMedia]` 로그(미존재 warn, `found media`·`filter check` info)가 한 번도 찍힌 적이 없다는 사람 확인(2026-11-13). 이 로그는 `mediaPublicId`가 null이 아니면 반드시 찍혔으므로, 운영에서 두 경로에 media ID가 담긴 요청이 resolve까지 온 적이 없다는 뜻이다. 따라서 이번 변경으로 400이 되는 기존 요청은 없을 것으로 본다.
  - 단서: 운영 로그 레벨에서 info가 출력되는지와 로그 보존 기간은 미기록이다. warn(미존재)도 없었으므로 미존재 경로는 레벨과 무관하게 발생하지 않았다.
  - 앱이 두 경로에서 프로필 이미지를 아예 보내지 않는지, 다른 필드명으로 보내는지는 미확인이다(범위 밖).
- 프로필 수정 실패 시 닉네임 변경도 함께 롤백된다. 이전에는 닉네임만 반영되고 200이었다.
- 현재 media 재전송 예외는 프로필 수정에만 있다. 초대 재가입의 재전송은 계속 MEDIA018이다.
- 팀 생성 실패 시 Team INSERT 후 롤백되므로 ID 시퀀스에 빈 번호가 생긴다. 동작 영향은 없다.
- 디버그 로그를 지웠으므로 실패 원인(미존재·소유자·purpose·상태 중 무엇인지)은 로그에 남지 않는다. 실패 자체는 `GlobalExceptionHandler.handleBusinessException`의 warn 로그(`Business Exception: ...`)와 요청 URI가 담긴 MEDIA018 응답으로 드러난다.
