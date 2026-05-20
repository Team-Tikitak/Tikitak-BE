package kusitms.spin.tikitak.domain.member.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ProfileCharacterType {
	TAK_LEADER("default-profiles/tak-leader.png"),
	TAK_SPARK("default-profiles/tak-spark.png"),
	TAK_BURNER("default-profiles/tak-burner.png"),
	TAK_BUILDER("default-profiles/tak-builder.png"),
	TAK_FREE("default-profiles/tak-free.png"),
	TAK_CARE("default-profiles/tak-care.png");

	private final String imagePath;
}
