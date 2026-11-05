package com.poomitda.domain.post.entity;

import com.poomitda.domain.member.entity.Member;
import com.poomitda.global.entity.BaseCreatedTimeEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
    name = "bookmarks",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_bookmarks_member_post",
            columnNames = {"member_id", "post_id"}
        )
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Bookmark extends BaseCreatedTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "bookmark_id", nullable = false)
  private Long bookmarkId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "member_id", nullable = false, updatable = false)
  private Member member;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "post_id", nullable = false, updatable = false)
  private Post post;

  public Bookmark(Member member, Post post) {
    this.member = member;
    this.post = post;
  }
}
