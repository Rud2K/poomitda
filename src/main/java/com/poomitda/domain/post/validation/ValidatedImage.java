package com.poomitda.domain.post.validation;

public record ValidatedImage(
    byte[] bytes,
    ImageFormat format
) {}
