package com.poomitda.domain.matching.entity.constant;

public enum ApplicationStatus {
  APPLIED,      // 신청 접수
  UNDER_REVIEW, // 심사 중
  APPROVED,     // 승인, 인계 완료 전
  REJECTED,     // 거절
  CANCELLED,    // 신청자 취소
  COMPLETED;    // 인계 완료
}
