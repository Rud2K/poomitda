package com.poomitda.domain.auth.entity;

import com.poomitda.domain.member.entity.Member;
import com.poomitda.global.entity.BaseUpdatedTimeEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;

@Entity
@Table(
    name = "refresh_tokens",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_refresh_tokens_member",
            columnNames = "member_id"
        )
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken extends BaseUpdatedTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "refresh_token_id", nullable = false)
  private Long refreshTokenId;

  @OneToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "member_id", nullable = false, updatable = false)
  private Member member;

  @Column(name = "token_hash", length = 64, nullable = false)
  private String tokenHash;

  @Column(name = "expires_at", nullable = false)
  private LocalDateTime expiresAt;

  public RefreshToken(
      Member member,
      String tokenHash,
      LocalDateTime expiresAt
  ) {
    this.member = member;
    this.tokenHash = tokenHash;
    this.expiresAt = expiresAt;
  }

  /**
   * 새로 발급한 Refresh Token의 해시와 만료 시각으로 교체한다.
   *
   * @param tokenHash 새 토큰의 해시
   * @param expiresAt 새 토큰의 만료 시각
   */
  public void rotate(String tokenHash, LocalDateTime expiresAt) {
    this.tokenHash = tokenHash;
    this.expiresAt = expiresAt;
  }
}
