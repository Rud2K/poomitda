package com.poomitda.global.entity;

import static org.junit.jupiter.api.Assertions.*;

import com.poomitda.global.config.JpaAuditingConfig;
import jakarta.persistence.*;
import java.time.*;
import java.util.Optional;
import lombok.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Import;
import org.springframework.data.auditing.*;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@AutoConfigureTestDatabase(replace = Replace.ANY)
@ActiveProfiles("test")
@Import(JpaAuditingConfig.class)
@EntityScan(basePackageClasses = BaseTimeEntityTest.class)
class BaseTimeEntityTest {

  @PersistenceContext
  private EntityManager entityManager;

  @Autowired
  private AuditingHandler auditingHandler;

  private LocalDateTime currentTime;

  @BeforeEach
  void setUp() {
    currentTime = LocalDateTime.of(2026, 1, 1, 12, 0);
    auditingHandler.setDateTimeProvider(
        () -> Optional.of(currentTime)
    );
  }

  @AfterEach
  void tearDown() {
    auditingHandler.setDateTimeProvider(CurrentDateTimeProvider.INSTANCE);
  }

  // 생성 및 수정 시각 검증에 사용할 테스트 전용 엔티티
  @Entity
  @Table(name = "test_auditing_entity")
  @Getter
  @NoArgsConstructor(access = AccessLevel.PROTECTED)
  public static class TestEntity extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Setter
    @Column(name = "name", nullable = false)
    private String name;

    public TestEntity(String name) {
      this.name = name;
    }
  }

  @Test
  @DisplayName("최초 저장 -> 생성 및 수정 시각 기록")
  void createdAndUpdatedAtOnPersistTest() {
    // given
    TestEntity testEntity = new TestEntity("최초 이름");

    // when
    entityManager.persist(testEntity);
    entityManager.flush();

    Long id = testEntity.getId();
    entityManager.clear();

    TestEntity savedEntity = entityManager.find(TestEntity.class, id);

    // then
    assertNotNull(savedEntity);
    assertEquals("최초 이름", savedEntity.getName());
    assertNotNull(savedEntity.getCreatedAt());
    assertNotNull(savedEntity.getUpdatedAt());
    assertEquals(savedEntity.getCreatedAt(), savedEntity.getUpdatedAt());
  }

  @Test
  @DisplayName("수정 저장 -> 생성 시각 유지 & 수정 시각 갱신")
  void updatedAtOnUpdateTest() {
    // given
    LocalDateTime createdTime = currentTime;
    TestEntity testEntity = new TestEntity("변경 전 이름");

    entityManager.persist(testEntity);
    entityManager.flush();

    Long id = testEntity.getId();
    entityManager.clear();

    TestEntity savedEntity = entityManager.find(TestEntity.class, id);
    LocalDateTime modifiedTime = currentTime.plusHours(1);

    // when
    currentTime = modifiedTime;
    savedEntity.setName("변경 후 이름");

    entityManager.flush();
    entityManager.clear();

    TestEntity updatedEntity = entityManager.find(TestEntity.class, id);

    // then
    assertNotNull(updatedEntity);
    assertEquals("변경 후 이름", updatedEntity.getName());
    assertEquals(createdTime, updatedEntity.getCreatedAt());
    assertEquals(modifiedTime, updatedEntity.getUpdatedAt());
  }
}