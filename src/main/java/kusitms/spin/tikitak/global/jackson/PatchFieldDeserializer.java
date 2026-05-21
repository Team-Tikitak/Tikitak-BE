package kusitms.spin.tikitak.global.jackson;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.deser.ContextualDeserializer;
import kusitms.spin.tikitak.global.dto.PatchField;

import java.io.IOException;

public class PatchFieldDeserializer extends JsonDeserializer<PatchField<?>> implements ContextualDeserializer {

	private final JavaType valueType;

	public PatchFieldDeserializer() {
		this.valueType = null;
	}

	private PatchFieldDeserializer(JavaType valueType) {
		this.valueType = valueType;
	}

	@Override
	public PatchField<?> deserialize(JsonParser parser, DeserializationContext context) throws IOException {
		JsonNode node = parser.getCodec().readTree(parser);
		if (node == null || node.isNull()) {
			return PatchField.of(null);
		}
		Object value = context.readTreeAsValue(node, valueType);
		return PatchField.of(value);
	}

	@Override
	public JsonDeserializer<?> createContextual(DeserializationContext context, BeanProperty property) {
		JavaType contextualType = property.getType().containedType(0);
		return new PatchFieldDeserializer(contextualType);
	}
}
