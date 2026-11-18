package com.poomitda.domain.matching.cursor;

import com.poomitda.domain.matching.entity.constant.ApplicationStatus;
import java.time.LocalDateTime;

public record MyApplicationCursor(
    Integer version,
    Long memberId,
    ApplicationStatus status,
    LocalDateTime createdAt,
    Long applicationId
) {}
