package com.poomitda.domain.post.cursor;

import com.poomitda.domain.post.dto.constant.PostStatusFilter;
import com.poomitda.domain.post.entity.constant.*;
import com.poomitda.global.constant.*;

public record PostCursorFilter(
    NoticeType noticeType,
    Species species,
    Gender gender,
    Region region,
    String district,
    PostStatusFilter status,
    Integer minAgeMonths,
    Integer maxAgeMonths,
    String keyword
) {}
