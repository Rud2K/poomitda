package com.poomitda.domain.post.dto.constant;

public enum PostSort {
  LATEST,   // 최신 등록 순. 생성 시각 내림차순, 같으면 공고 ID 내림차순
  BOOKMARK, // 북마크 많은 순. 북마크 수 내림차순, 같으면 공고 ID 내림차순
  CLOSING;  // 마감 임박순. 마감 시각 오름차순, 같으면 공고 ID 오름차순. 마감 시각 없는 공고 제외
}
