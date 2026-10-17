package com.poomitda.global.security.principal;

import com.poomitda.domain.member.entity.constant.MemberRole;

public record MemberPrincipal(
    Long memberId,
    MemberRole role
) {}
