package com.poomitda.global.request;

import tools.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(using = PatchFieldDeserializer.class)
public record PatchField<T>(
    boolean present,
    T value
) {
  public static <T> PatchField<T> absent() {
    return new PatchField<>(false, null);
  }

  public static <T> PatchField<T> supplied(T value) {
    return new PatchField<>(true, value);
  }
}
