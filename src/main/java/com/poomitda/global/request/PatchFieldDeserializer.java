package com.poomitda.global.request;

import tools.jackson.core.*;
import tools.jackson.databind.*;

public class PatchFieldDeserializer extends ValueDeserializer<PatchField<?>> {

  private final JavaType valueType;

  public PatchFieldDeserializer() {
    this.valueType = null;
  }

  private PatchFieldDeserializer(JavaType valueType) {
    this.valueType = valueType;
  }

  @Override
  public ValueDeserializer<?> createContextual(
      DeserializationContext ctxt,
      BeanProperty property
  ) throws JacksonException {
    // 현재 처리할 PatchField<T>의 타입 확인
    JavaType patchFieldType = ctxt.getContextualType();

    if (patchFieldType == null && property != null) {
      patchFieldType = property.getType();
    }

    if (patchFieldType == null
        || !patchFieldType.hasRawClass(PatchField.class)
        || patchFieldType.containedTypeCount() != 1) {
      return ctxt.reportBadDefinition(
          ctxt.constructType(PatchField.class),
          "PatchField는 내부 값의 타입을 명시해야 합니다."
      );
    }

    // PatchField<T>에서 T에 해당하는 타입 추출
    JavaType valueType = patchFieldType.containedType(0);

    // 해당 내부 타입을 처리하는 변환기 생성
    return new PatchFieldDeserializer(valueType);
  }

  @Override
  public PatchField<?> deserialize(
      JsonParser p,
      DeserializationContext ctxt
  ) throws JacksonException {
    if (valueType == null) {
      return ctxt.reportBadDefinition(
          ctxt.constructType(PatchField.class),
          "PatchField의 내부 값 타입이 초기화되지 않았습니다."
      );
    }

    // JSON 값을 내부 타입으로 변환한 뒤 전달된 값으로 감싸기
    Object value = ctxt.readValue(p, valueType);
    return PatchField.supplied(value);
  }

  @Override
  public PatchField<?> getNullValue(
      DeserializationContext ctxt
  ) throws JacksonException {
    return PatchField.supplied(null);
  }

  @Override
  public PatchField<?> getAbsentValue(
      DeserializationContext ctxt
  ) throws JacksonException {
    return PatchField.absent();
  }
}
