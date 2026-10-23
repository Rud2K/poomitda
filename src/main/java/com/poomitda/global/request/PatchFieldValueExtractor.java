package com.poomitda.global.request;

import jakarta.validation.valueextraction.*;

public class PatchFieldValueExtractor implements ValueExtractor<PatchField<@ExtractedValue ?>> {

  @Override
  public void extractValues(
      PatchField<?> originalValue,
      ValueReceiver receiver
  ) {
    // 생략된 필드는 검증할 값을 전달하지 않음
    if (!originalValue.present()) {
      return;
    }

    // 전달된 필드는 null을 포함하여 내부 값을 검증기에 전달
    receiver.value(null, originalValue.value());
  }
}
