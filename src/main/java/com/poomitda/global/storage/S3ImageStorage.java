package com.poomitda.global.storage;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

@Component
@RequiredArgsConstructor
public class S3ImageStorage {

  private final S3Client s3Client;
  private final S3Properties properties;

  public void upload(String imageKey, byte[] imageBytes, String contentType) {
    PutObjectRequest request = PutObjectRequest.builder()
        .bucket(properties.bucket())
        .key(imageKey)
        .contentType(contentType)
        .build();

    s3Client.putObject(request, RequestBody.fromBytes(imageBytes));
  }

  public void delete(String imageKey) {
    DeleteObjectRequest request = DeleteObjectRequest.builder()
        .bucket(properties.bucket())
        .key(imageKey)
        .build();

    s3Client.deleteObject(request);
  }

  public String getImageUrl(String imageKey) {
    GetUrlRequest request = GetUrlRequest.builder()
        .bucket(properties.bucket())
        .key(imageKey)
        .build();

    return s3Client.utilities()
        .getUrl(request)
        .toExternalForm();
  }
}
