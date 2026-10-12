package com.poomitda.domain.member.entity;

import com.poomitda.domain.member.entity.constant.*;
import com.poomitda.global.constant.*;
import com.poomitda.global.entity.BaseDeletedTimeEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
    name = "members",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_members_email",
            columnNames = "email"
        ),
        @UniqueConstraint(
            name = "uk_members_nickname",
            columnNames = "nickname"
        )
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member extends BaseDeletedTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "member_id", nullable = false)
  private Long memberId;            // 회원 ID

  @Column(name = "email", length = 100, nullable = false)
  private String email;             // 이메일

  @Column(name = "password", length = 255, nullable = false)
  private String password;          // 비밀번호

  @Column(name = "nickname", length = 50, nullable = false)
  private String nickname;          // 닉네임

  @Column(name = "profile_image_key", length = 1024)
  private String profileImageKey;   // 이미지파일 식별 Key

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(name = "preferred_region", length = 20)
  private Region preferredRegion;   // 선호 지역

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(name = "preferred_species", length = 20)
  private Species preferredSpecies; // 선호 동물 종류

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(name = "role", length = 20, nullable = false)
  private MemberRole role;          // 권한

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(name = "status", length = 20, nullable = false)
  private MemberStatus status;      // 상태

  @Column(name = "rating_sum", nullable = false)
  private long ratingSum;           // 받은 평점 합계

  @Column(name = "review_count", nullable = false)
  private int reviewCount;          // 받은 후기 수

  public Member(
      String email,
      String encodedPassword,
      String nickname,
      Region preferredRegion,
      Species preferredSpecies
  ) {
    this.email = email;
    this.password = encodedPassword;
    this.nickname = nickname;
    this.profileImageKey = null;
    this.preferredRegion = preferredRegion;
    this.preferredSpecies = preferredSpecies;
    this.role = MemberRole.ROLE_USER;
    this.status = MemberStatus.ACTIVE;
    this.ratingSum = 0L;
    this.reviewCount = 0;
  }
}
