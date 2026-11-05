package com.poomitda.domain.matching.entity.constant;

public enum ApplicationRejectionReason {
  SCREENING_REJECTED, // 심사 결과 거절
  OTHER_SELECTED,     // 다른 신청자가 선정되어 자동 거절
  POST_CLOSED,        // 공고 모집 종료에 따른 거절
  HANDOVER_CANCELLED, // 승인 후 인계 취소 확정
  ADMIN_ACTION;       // 관리자 조치에 따른 거절
}
