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

  // 생성 시각 검증용 테스트 엔티티
  @Entity
  @Table
  @Getter
  @NoArgsConstructor(access = AccessLevel.PROTECTED)
  public static class CreatedTimeTestEntity extends BaseCreatedTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Setter
    @Column(nullable = false)
    private String name;

    public CreatedTimeTestEntity(String name) {
      this.name = name;
    }
  }

  // 수정 시각 검증용 테스트 엔티티
  @Entity
  @Table
  @Getter
  @NoArgsConstructor(access = AccessLevel.PROTECTED)
  public static class UpdatedTimeTestEntity extends BaseUpdatedTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Setter
    @Column(nullable = false)
    private String name;

    public UpdatedTimeTestEntity(String name) {
      this.name = name;
    }
  }

  // 삭제 시각 검증용 테스트 엔티티
  @Entity
  @Table
  @Getter
  @NoArgsConstructor(access = AccessLevel.PROTECTED)
  public static class DeletedTimeTestEntity extends BaseDeletedTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Setter
    @Column(nullable = false)
    private String name;

    public DeletedTimeTestEntity(String name) {
      this.name = name;
    }

    public void delete(LocalDateTime deletedAt) {
      markDeleted(deletedAt);
    }
  }

  @Test
  @DisplayName("생성 시각 엔티티 저장 -> 생성 시각 기록")
  void createdAtOnPersistTest() {
    // given
    LocalDateTime createdTime = currentTime;
    CreatedTimeTestEntity testEntity = new CreatedTimeTestEntity("최초 이름");

    // when
    entityManager.persist(testEntity);
    entityManager.flush();

    Long id = testEntity.getId();
    entityManager.clear();

    CreatedTimeTestEntity savedEntity = entityManager.find(CreatedTimeTestEntity.class, id);

    // then
    assertNotNull(savedEntity);
    assertEquals("최초 이름", savedEntity.getName());
    assertEquals(createdTime, savedEntity.getCreatedAt());
  }

  @Test
  @DisplayName("생성 시각 엔티티 수정 -> 생성 시각 유지")
  void createdAtOnUpdateTest() {
    // given
    LocalDateTime createdTime = currentTime;
    CreatedTimeTestEntity testEntity = new CreatedTimeTestEntity("변경 전");

    entityManager.persist(testEntity);
    entityManager.flush();

    Long id = testEntity.getId();
    entityManager.clear();

    CreatedTimeTestEntity savedEntity = entityManager.find(CreatedTimeTestEntity.class, id);

    // when
    currentTime = createdTime.plusHours(1);
    savedEntity.setName("변경 후");

    entityManager.flush();
    entityManager.clear();

    CreatedTimeTestEntity updatedEntity = entityManager.find(CreatedTimeTestEntity.class, id);

    // then
    assertNotNull(updatedEntity);
    assertEquals("변경 후", updatedEntity.getName());
    assertEquals(createdTime, updatedEntity.getCreatedAt());
  }

  @Test
  @DisplayName("최초 저장 -> 생성 및 수정 시각 기록")
  void createdAndUpdatedAtOnPersistTest() {
    // given
    UpdatedTimeTestEntity testEntity = new UpdatedTimeTestEntity("최초 이름");

    // when
    entityManager.persist(testEntity);
    entityManager.flush();

    Long id = testEntity.getId();
    entityManager.clear();

    UpdatedTimeTestEntity savedEntity = entityManager.find(UpdatedTimeTestEntity.class, id);

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
    UpdatedTimeTestEntity testEntity = new UpdatedTimeTestEntity("변경 전 이름");

    entityManager.persist(testEntity);
    entityManager.flush();

    Long id = testEntity.getId();
    entityManager.clear();

    UpdatedTimeTestEntity savedEntity = entityManager.find(UpdatedTimeTestEntity.class, id);
    LocalDateTime modifiedTime = currentTime.plusHours(1);

    // when
    currentTime = modifiedTime;
    savedEntity.setName("변경 후 이름");

    entityManager.flush();
    entityManager.clear();

    UpdatedTimeTestEntity updatedEntity = entityManager.find(UpdatedTimeTestEntity.class, id);

    // then
    assertNotNull(updatedEntity);
    assertEquals("변경 후 이름", updatedEntity.getName());
    assertEquals(createdTime, updatedEntity.getCreatedAt());
    assertEquals(modifiedTime, updatedEntity.getUpdatedAt());
  }

  @Test
  @DisplayName("삭제 전 저장 -> 삭제 시각은 null")
  void deletedAtOnPersistTest() {
    // given
    LocalDateTime createdTime = currentTime;
    DeletedTimeTestEntity testEntity = new DeletedTimeTestEntity("삭제 전");

    // when
    entityManager.persist(testEntity);
    entityManager.flush();

    Long id = testEntity.getId();
    entityManager.clear();

    DeletedTimeTestEntity savedEntity = entityManager.find(DeletedTimeTestEntity.class, id);

    // then
    assertNotNull(savedEntity);
    assertEquals(createdTime, savedEntity.getCreatedAt());
    assertEquals(createdTime, savedEntity.getUpdatedAt());
    assertNull(savedEntity.getDeletedAt());
  }

  @Test
  @DisplayName("삭제 처리 -> 삭제 시각 기록 및 행 유지")
  void deletedAtOnDeleteTest() {
    // given
    LocalDateTime createdTime = currentTime;
    DeletedTimeTestEntity testEntity = new DeletedTimeTestEntity("삭제 대상");

    entityManager.persist(testEntity);
    entityManager.flush();

    Long id = testEntity.getId();
    entityManager.clear();

    DeletedTimeTestEntity savedEntity = entityManager.find(DeletedTimeTestEntity.class, id);

    LocalDateTime deletedTime = currentTime.plusMinutes(1);
    LocalDateTime modifiedTime = currentTime.plusHours(1);

    // when
    currentTime = modifiedTime;
    savedEntity.delete(deletedTime);

    entityManager.flush();
    entityManager.clear();

    DeletedTimeTestEntity deletedEntity = entityManager.find(DeletedTimeTestEntity.class, id);

    // then
    assertNotNull(deletedEntity);
    assertEquals("삭제 대상", deletedEntity.getName());
    assertEquals(createdTime, deletedEntity.getCreatedAt());
    assertEquals(modifiedTime, deletedEntity.getUpdatedAt());
    assertEquals(deletedTime, deletedEntity.getDeletedAt());
  }

  @Test
  @DisplayName("삭제 재호출 -> 최초 삭제 시각 유지")
  void preserveFirstDeletedAtTest() {
    // given
    DeletedTimeTestEntity testEntity = new DeletedTimeTestEntity("삭제 대상");

    entityManager.persist(testEntity);
    entityManager.flush();

    LocalDateTime firstDeletedTime = currentTime.plusHours(1);
    currentTime = firstDeletedTime;
    testEntity.delete(firstDeletedTime);
    entityManager.flush();

    Long id = testEntity.getId();
    entityManager.clear();

    DeletedTimeTestEntity savedEntity = entityManager.find(DeletedTimeTestEntity.class, id);

    // when
    currentTime = firstDeletedTime.plusHours(1);
    savedEntity.delete(currentTime);

    entityManager.flush();
    entityManager.clear();

    DeletedTimeTestEntity deletedEntity = entityManager.find(DeletedTimeTestEntity.class, id);

    // then
    assertNotNull(deletedEntity);
    assertEquals(firstDeletedTime, deletedEntity.getDeletedAt());
  }

  @Test
  @DisplayName("삭제 시각 null -> 예외 발생 및 기존 값 유지")
  void rejectNullDeletedAtTest() {
    // given
    DeletedTimeTestEntity activeEntity = new DeletedTimeTestEntity("삭제 전");
    DeletedTimeTestEntity deletedEntity = new DeletedTimeTestEntity("삭제 후");
    LocalDateTime firstDeletedTime = currentTime;

    deletedEntity.delete(firstDeletedTime);

    // when
    NullPointerException activeException = assertThrows(
        NullPointerException.class,
        () -> activeEntity.delete(null)
    );

    NullPointerException deletedException = assertThrows(
        NullPointerException.class,
        () -> deletedEntity.delete(null)
    );

    // then
    String expectedMessage =
        "삭제 시각 기록 실패: DeletedTimeTestEntity.markDeleted()에 null이 전달되었습니다.";

    assertEquals(expectedMessage, activeException.getMessage());
    assertEquals(expectedMessage, deletedException.getMessage());
    assertNull(activeEntity.getDeletedAt());
    assertEquals(firstDeletedTime, deletedEntity.getDeletedAt());
  }
}