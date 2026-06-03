package kusitms.spin.tikitak.global.dto;

import lombok.Getter;

@Getter
public class PatchField<T> {

	private static final PatchField<?> UNDEFINED = new PatchField<>(false, null);

	private final boolean defined;
	private final T value;

	private PatchField(boolean defined, T value) {
		this.defined = defined;
		this.value = value;
	}

	@SuppressWarnings("unchecked")
	public static <T> PatchField<T> undefined() {
		return (PatchField<T>) UNDEFINED;
	}

	public static <T> PatchField<T> of(T value) {
		return new PatchField<>(true, value);
	}
}
