package com.poomitda.global.validation;

import com.poomitda.global.constant.Region;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public class DistrictCatalog {

  private static final String RESOURCE_PATH = "region/districts.csv";

  private final Map<Region, Set<String>> districtsByRegion;

  public DistrictCatalog() {
    this.districtsByRegion = loadDistricts();
  }

  public boolean contains(Region region, String district) {
    if (region == null || district == null) return false;
    Set<String> districts = districtsByRegion.get(region);
    return districts != null && districts.contains(district);
  }

  private Map<Region, Set<String>> loadDistricts() {
    Map<Region, Set<String>> loaded = new EnumMap<>(Region.class);
    ClassPathResource resource = new ClassPathResource(RESOURCE_PATH);

    try (BufferedReader reader = new BufferedReader(
        new InputStreamReader(
            resource.getInputStream(),
            StandardCharsets.UTF_8
        )
    )) {
      String header = reader.readLine();

      // UTF-8 BOM이 있는 파일도 읽을 수 있도록 첫 글자만 제거
      if (header != null && header.startsWith("\uFEFF")) {
        header = header.substring(1);
      }

      if (!"region,district".equals(header)) {
        throw new IllegalStateException("시/군/구 CSV 헤더가 올바르지 않습니다.");
      }

      String line;
      int lineNumber = 1;

      while ((line = reader.readLine()) != null) {
        lineNumber++;

        if (line.isBlank()) {
          continue;
        }

        String[] columns =  line.split(",", -1);

        if (columns.length != 2) {
          throw new IllegalStateException("시/군/구 CSV 열 개수가 올바르지 않습니다. 행: " + lineNumber);
        }

        Region region = parseRegion(columns[0].strip(), lineNumber);
        String district = columns[1].strip();

        if (district.isEmpty() || district.length() > 50) {
          throw new IllegalStateException("시/군/구 CSV 지역명이 올바르지 않습니다. 행: " + lineNumber);
        }

        if (region == Region.SEJONG) {
          throw new IllegalStateException("세종은 시/군/구 목록에 포함하지 않습니다. 행: " + lineNumber);
        }

        Set<String> districts = loaded.computeIfAbsent(region, key -> new HashSet<>());

        if (!districts.add(district)) {
          throw new IllegalStateException("시/군/구 CSV에 중복 지역이 있습니다. 행: " + lineNumber);
        }
      }
    } catch (IOException exception) {
      throw new IllegalStateException(
          "시/군/구 기준 데이터를 읽을 수 없습니다: " + RESOURCE_PATH,
          exception
      );
    }

    // 세종을 제외한 모든 광역 지역에 데이터가 있는지 확인
    for (Region region : Region.values()) {
      if (region != Region.SEJONG && !loaded.containsKey(region)) {
        throw new IllegalStateException("시/군/구 기준 데이터가 누락된 지역입니다: " + region);
      }
    }

    // 내부 Set과 외부 Map을 모두 변경 불가능하게 보관
    loaded.replaceAll((region, districts) -> Set.copyOf(districts));
    return Map.copyOf(loaded);
  }

  private Region parseRegion(String value, int lineNumber) {
    try {
      return Region.valueOf(value);
    } catch (IllegalArgumentException exception) {
      throw new IllegalStateException(
          "시/군/구 CSV 지역 코드가 올바르지 않습니다. 행: " + lineNumber,
          exception
      );
    }
  }
}
