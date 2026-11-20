package com.poomitda.domain.matching.cursor;

import com.poomitda.domain.matching.entity.constant.ApplicationStatus;
import java.time.LocalDateTime;

public record PostApplicationCursor(
    Integer version,
    Long memberId,
    Long postId,
    ApplicationStatus status,
    LocalDateTime createdAt,
    Long applicationId
) {}
