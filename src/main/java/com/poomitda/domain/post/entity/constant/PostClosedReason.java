package com.poomitda.domain.post.entity.constant;

public enum PostClosedReason {
  MATCHED,            // 신청 승인에 따른 모집 종료
  MANUAL,             // 보호자의 모집 중단
  ADMIN,              // 관리자에 의한 종료
  HANDOVER_CANCELLED, // 승인 후 인계 취소
  NO_APPLICANTS,      // 신청 이력 없이 종료
  NO_SELECTION;       // 신청 이력은 있으나 선정 없이 종료
}
