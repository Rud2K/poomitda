package com.poomitda.domain.post.entity;

import com.poomitda.domain.member.entity.Member;
import com.poomitda.domain.post.entity.constant.*;
import com.poomitda.global.constant.*;
import com.poomitda.global.entity.BaseDeletedTimeEntity;
import jakarta.persistence.*;
import java.time.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "posts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Post extends BaseDeletedTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "post_id", nullable = false)
  private Long postId;                        // 공고 ID

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "member_id", nullable = false, updatable = false)
  private Member author;                      // 공고 작성자

  @Column(name = "title", length = 100, nullable = false)
  private String title;                       // 제목

  @Column(name = "content", columnDefinition = "TEXT", nullable = false)
  private String content;                     // 본문

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(name = "notice_type", length = 20, nullable = false)
  private NoticeType noticeType;              // 모집 유형

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(name = "species", length = 50, nullable = false)
  private Species species;                    // 동물 종류

  @Column(name = "breed", length = 50, nullable = false)
  private String breed;                       // 품종

  @Column(name = "estimated_birth_date")
  private LocalDate estimatedBirthDate;       // 추정 출생일

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(name = "gender", length = 10, nullable = false)
  private Gender gender;                      // 성별

  @Column(name = "is_neutered", nullable = false)
  private boolean isNeutered;                 // 중성화 여부

  @Column(name = "foster_period", length = 100)
  private String fosterPeriod;                // 임시보호 희망 기간

  @Column(name = "found_location")
  private String foundLocation;               // 발견 장소

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(name = "region", length = 20, nullable = false)
  private Region region;                      // 검색용 행정구역

  @Column(name = "district", length = 50)
  private String district;                    // 세부 검색용 시/군/구

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(name = "status", length = 20, nullable = false)
  private PostStatus status;                  // 공고 상태

  @Column(name = "bookmark_count", nullable = false)
  private int bookmarkCount;                  // 북마크 수

  @Column(name = "recruitment_deadline")
  private LocalDateTime recruitmentDeadline;  // 접수 마감 시각

  @Column(name = "closed_at")
  private LocalDateTime closedAt;             // 모집 종료 시각

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(name = "closed_reason", length = 20)
  private PostClosedReason closedReason;      // 모집 종료 사유

  public Post(
      Member author,
      String title,
      String content,
      NoticeType noticeType,
      Species species,
      String breed,
      LocalDate estimatedBirthDate,
      Gender gender,
      boolean isNeutered,
      String fosterPeriod,
      String foundLocation,
      Region region,
      String district,
      LocalDateTime recruitmentDeadline
  ) {
    this.author = author;
    this.title = title;
    this.content = content;
    this.noticeType = noticeType;
    this.species = species;
    this.breed = breed;
    this.estimatedBirthDate = estimatedBirthDate;
    this.gender = gender;
    this.isNeutered = isNeutered;
    this.fosterPeriod = fosterPeriod;
    this.foundLocation = foundLocation;
    this.region = region;
    this.district = district;
    this.status = PostStatus.RECRUITING;
    this.bookmarkCount = 0;
    this.recruitmentDeadline = recruitmentDeadline;
    this.closedAt = null;
    this.closedReason = null;
  }
}
