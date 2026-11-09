package com.poomitda.domain.post.dto.constant;

public enum PostStatusFilter {
  RECRUITING,       // 접수 중인 공고 조회 (접수 마감 시각도 함께 확인)
  RECEPTION_CLOSED, // 신규 접수는 종료되고 기존 신청을 심사하는 공고 조회
  CLOSED,           // 모집 절차가 종료된 공고 조회
  ALL;              // 모집 상태와 관계없이 조회 (삭제된 공고는 제외)
}
