package com.poomitda.domain.matching.entity;

import com.poomitda.domain.matching.entity.constant.*;
import com.poomitda.domain.member.entity.Member;
import com.poomitda.domain.post.entity.Post;
import com.poomitda.global.entity.BaseUpdatedTimeEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
    name = "applications",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_applications_post_applicant",
            columnNames = {"post_id", "applicant_id"}
        )
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Application extends BaseUpdatedTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "application_id", nullable = false, updatable = false)
  private Long applicationId;                           // 신청서 ID

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "post_id", nullable = false, updatable = false)
  private Post post;                                    // 신청 대상 공고

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "applicant_id", nullable = false, updatable = false)
  private Member applicant;                             // 신청서를 제출한 회원

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "completed_by")
  private Member completedBy;                           // 인계 완료 처리를 수행한 회원

  @Column(name = "occupation", length = 50, nullable = false)
  private String occupation;                            // 신청자의 직업

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(name = "housing_type", length = 30, nullable = false)
  private HousingType housingType;                      // 주거 형태

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(name = "housing_ownership", length = 30, nullable = false)
  private HousingOwnership housingOwnership;            // 주거 소유 형태

  @Column(name = "is_landlord_agreed")
  private Boolean isLandlordAgreed;                     // 임대인의 반려동물 양육 동의 여부

  @Column(name = "has_yard_or_balcony", nullable = false)
  private boolean hasYardOrBalcony;                     // 마당 또는 발코니 보유 여부

  @Column(name = "time_away_per_day", nullable = false)
  private int timeAwayPerDay;                           // 하루 평균 부재 시간. (0~24)

  @Column(name = "family_count", nullable = false)
  private int familyCount;                              // 가족 수 (0 이상)

  @Column(name = "family_ages", length = 100, nullable = false)
  private String familyAges;                            // 가족 구성원의 연령 정보

  @Column(name = "has_cohabitant_agreed", nullable = false)
  private boolean hasCohabitantAgreed;                  // 동거인의 반려동물 양육 동의 여부

  @Column(name = "has_allergy", nullable = false)
  private boolean hasAllergy;                           // 반려동물 관련 알레르기 유무

  @Column(name = "current_pets", columnDefinition = "TEXT", nullable = false)
  private String currentPets;                           // 현재 양육 중인 반려동물 정보

  @Column(name = "past_experience_detail", columnDefinition = "TEXT")
  private String pastExperienceDetail;                  // 과거 반려동물 양육 경험 (선택 입력)

  @Column(name = "is_financially_ready", nullable = false)
  private boolean isFinanciallyReady;                   // 반려동물 양육에 필요한 경제적 준비 여부

  @Column(name = "pledge_agreed", nullable = false)
  private boolean pledgeAgreed;                         // 입양 및 임시보호 서약 동의 여부 (신청 시 true 필수)

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(name = "status", length = 20, nullable = false)
  private ApplicationStatus status;                     // 신청 처리 상태 (최초 생성 시 APPLIED)

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(name = "completion_method", length = 20)
  private ApplicationCompletionMethod completionMethod; // 인계 완료 처리 방식

  @Column(name = "completed_at")
  private LocalDateTime completedAt;                    // 인계 완료 시각 (UTC 기준)

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(name = "rejection_reason", length = 30)
  private ApplicationRejectionReason rejectionReason;   // 신청 거절 사유

  @Column(name = "rejected_at")
  private LocalDateTime rejectedAt;                     // 신청 거절 시각 (UTC 기준)

  @Column(name = "approved_at")
  private LocalDateTime approvedAt;                     // 신청 승인 시각 (UTC 기준)

  public Application(
      Post post,
      Member applicant,
      String occupation,
      HousingType housingType,
      HousingOwnership housingOwnership,
      Boolean isLandlordAgreed,
      boolean hasYardOrBalcony,
      int timeAwayPerDay,
      int familyCount,
      String familyAges,
      boolean hasCohabitantAgreed,
      boolean hasAllergy,
      String currentPets,
      String pastExperienceDetail,
      boolean isFinanciallyReady,
      boolean pledgeAgreed
  ) {
    this.post = post;
    this.applicant = applicant;
    this.occupation = occupation;
    this.housingType = housingType;
    this.housingOwnership = housingOwnership;
    this.isLandlordAgreed = isLandlordAgreed;
    this.hasYardOrBalcony = hasYardOrBalcony;
    this.timeAwayPerDay = timeAwayPerDay;
    this.familyCount = familyCount;
    this.familyAges = familyAges;
    this.hasCohabitantAgreed = hasCohabitantAgreed;
    this.hasAllergy = hasAllergy;
    this.currentPets = currentPets;
    this.pastExperienceDetail = pastExperienceDetail;
    this.isFinanciallyReady = isFinanciallyReady;
    this.pledgeAgreed = pledgeAgreed;
    this.status = ApplicationStatus.APPLIED;
    this.completedBy = null;
    this.completionMethod = null;
    this.completedAt = null;
    this.rejectionReason = null;
    this.rejectedAt = null;
    this.approvedAt = null;
  }
}
