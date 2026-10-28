package com.poomitda.global.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.*;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.*;

class S3ImageStorageTest {

  private final S3Client s3Client = mock(S3Client.class);
  private final S3Properties properties = new S3Properties("ap-northeast-2", "poomitda-dev-images");
  private final S3ImageStorage storage = new S3ImageStorage(s3Client, properties);

  @Test
  @DisplayName("버킷과 이미지 키로 공개 이미지 URL을 생성한다")
  void createsImageUrl() {
    S3Utilities utilities = S3Utilities.builder()
        .region(Region.AP_NORTHEAST_2)
        .build();

    when(s3Client.utilities()).thenReturn(utilities);

    String imageUrl = storage.getImageUrl("posts/1/example.jpg");

    assertEquals(
        "https://poomitda-dev-images.s3.ap-northeast-2.amazonaws.com/posts/1/example.jpg",
        imageUrl
    );
  }

  @Test
  @DisplayName("이미지 키의 공백을 URL에 맞게 인코딩한다")
  void encodesSpacesInImageKey() {
    S3Utilities utilities = S3Utilities.builder()
        .region(Region.AP_NORTHEAST_2)
        .build();

    when(s3Client.utilities()).thenReturn(utilities);

    String imageUrl = storage.getImageUrl("posts/1/my photo.jpg");

    assertEquals(
        "https://poomitda-dev-images.s3.ap-northeast-2.amazonaws.com/posts/1/my%20photo.jpg",
        imageUrl
    );
  }
}