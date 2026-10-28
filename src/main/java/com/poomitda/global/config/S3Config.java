package com.poomitda.global.config;

import com.poomitda.global.storage.S3Properties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
@EnableConfigurationProperties(S3Properties.class)
public class S3Config {

  @Bean(destroyMethod = "close")
  public S3Client s3Client(S3Properties properties) {
    return S3Client.builder()
        .region(Region.of(properties.region()))
        .build();
  }
}
