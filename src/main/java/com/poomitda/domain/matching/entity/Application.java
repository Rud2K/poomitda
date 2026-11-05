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
  private Long applicationId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "post_id", nullable = false, updatable = false)
  private Post post;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "applicant_id", nullable = false, updatable = false)
  private Member applicant;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "completed_by")
  private Member completedBy;

  @Column(name = "occupation", length = 50, nullable = false)
  private String occupation;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(name = "housing_type", length = 30, nullable = false)
  private HousingType housingType;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(name = "housing_ownership", length = 30, nullable = false)
  private HousingOwnership housingOwnership;

  @Column(name = "is_landlord_agreed")
  private Boolean isLandlordAgreed;

  @Column(name = "has_yard_or_balcony", nullable = false)
  private boolean hasYardOrBalcony;

  @Column(name = "time_away_per_day", nullable = false)
  private int timeAwayPerDay;

  @Column(name = "family_count", nullable = false)
  private int familyCount;

  @Column(name = "family_ages", length = 100, nullable = false)
  private String familyAges;

  @Column(name = "has_cohabitant_agreed", nullable = false)
  private boolean hasCohabitantAgreed;

  @Column(name = "has_allergy", nullable = false)
  private boolean hasAllergy;

  @Column(name = "current_pets", columnDefinition = "TEXT", nullable = false)
  private String currentPets;

  @Column(name = "past_experience_detail", columnDefinition = "TEXT")
  private String pastExperienceDetail;

  @Column(name = "is_financially_ready", nullable = false)
  private boolean isFinanciallyReady;

  @Column(name = "pledge_agreed", nullable = false)
  private boolean pledgeAgreed;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(name = "status", length = 20, nullable = false)
  private ApplicationStatus status;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(name = "completion_method", length = 20)
  private ApplicationCompletionMethod completionMethod;

  @Column(name = "completed_at")
  private LocalDateTime completedAt;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(name = "rejection_reason", length = 30)
  private ApplicationRejectionReason rejectionReason;

  @Column(name = "rejected_at")
  private LocalDateTime rejectedAt;

  @Column(name = "approved_at")
  private LocalDateTime approvedAt;

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
