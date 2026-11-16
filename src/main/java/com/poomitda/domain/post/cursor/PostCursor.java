package com.poomitda.domain.post.cursor;

import com.poomitda.domain.post.dto.constant.PostSort;
import java.time.*;

public record PostCursor(
    Integer version,
    PostSort sort,
    Long postId,
    LocalDateTime createdAt,
    Integer bookmarkCount,
    LocalDateTime recruitmentDeadline,
    PostCursorFilter filters,
    LocalDate referenceDate
) {}
