package com.poomitda.domain.post.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "post_images")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostImage {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "post_image_id", nullable = false)
  private Long postImageId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "post_id", nullable = false, updatable = false)
  private Post post;

  @Column(name = "image_url", length = 255, nullable = false)
  private String imageUrl;

  @Column(name = "is_representative", nullable = false)
  private boolean isRepresentative;

  @Column(name = "display_order", nullable = false)
  private int displayOrder;

  public PostImage(
      Post post,
      String imageUrl,
      boolean isRepresentative,
      int displayOrder
  ) {
    this.post = post;
    this.imageUrl = imageUrl;
    this.isRepresentative = isRepresentative;
    this.displayOrder = displayOrder;
  }
}
