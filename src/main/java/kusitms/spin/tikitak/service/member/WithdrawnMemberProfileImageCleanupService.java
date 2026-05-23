package kusitms.spin.tikitak.service.member;

import kusitms.spin.tikitak.domain.media.entity.ObjectDeleteOutbox;
import kusitms.spin.tikitak.domain.member.entity.Member;
import kusitms.spin.tikitak.domain.member.enums.MemberStatus;
import kusitms.spin.tikitak.domain.team.entity.TeamMember;
import kusitms.spin.tikitak.global.config.R2Properties;
import kusitms.spin.tikitak.repository.member.MemberRepository;
import kusitms.spin.tikitak.repository.team.TeamMemberRepository;
import kusitms.spin.tikitak.service.media.ObjectDeleteOutboxService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class WithdrawnMemberProfileImageCleanupService {

	private final MemberRepository memberRepository;
	private final TeamMemberRepository teamMemberRepository;
	private final ObjectDeleteOutboxService objectDeleteOutboxService;
	private final ProfileImageObjectKeyResolver profileImageObjectKeyResolver;
	private final R2Properties r2Properties;

	@Transactional(readOnly = true)
	public List<Long> findCleanupTargetIds(LocalDateTime cutoff, int size) {
		return memberRepository.findWithdrawnProfileCleanupTargetIds(
				MemberStatus.INACTIVE,
				cutoff,
				PageRequest.of(0, size)
		);
	}

	@Transactional
	public boolean cleanUpProfileImages(Long memberId, LocalDateTime cutoff) {
		Member member = memberRepository.findProfileCleanupTargetForUpdate(memberId)
				.filter(target -> isCleanupEligible(target, cutoff))
				.orElse(null);
		if (member == null) {
			return false;
		}

		Set<String> objectKeys = new LinkedHashSet<>();
		collectObjectKey(objectKeys, member.getProfileImgUrl());
		member.clearProfileImage();

		List<TeamMember> teamMembers = teamMemberRepository.findProfileImageTargetsByMemberId(memberId);
		for (TeamMember teamMember : teamMembers) {
			collectObjectKey(objectKeys, teamMember.getProfileImgUrl());
			teamMember.clearProfileImage();
		}

		if (!objectKeys.isEmpty()) {
			objectDeleteOutboxService.saveAll(objectKeys.stream()
					.map(this::toDeleteRequest)
					.toList());
		}
		return true;
	}

	private boolean isCleanupEligible(Member member, LocalDateTime cutoff) {
		return member.getStatus() == MemberStatus.INACTIVE
				&& member.getDeletedAt() != null
				&& !member.getDeletedAt().isAfter(cutoff);
	}

	private void collectObjectKey(Set<String> objectKeys, String profileImgUrl) {
		profileImageObjectKeyResolver.resolveObjectKey(profileImgUrl)
				.ifPresent(objectKeys::add);
	}

	private ObjectDeleteOutbox toDeleteRequest(String objectKey) {
		return ObjectDeleteOutbox.builder()
				.bucket(r2Properties.getBucketName())
				.objectKey(objectKey)
				.build();
	}
}
