package com.poomitda.global.security.jwt;

import com.poomitda.domain.member.service.MemberAuthenticationService;
import com.poomitda.global.security.handler.CustomAuthenticationEntryPoint;
import com.poomitda.global.security.principal.MemberPrincipal;
import io.jsonwebtoken.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.*;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.*;
import org.springframework.web.filter.OncePerRequestFilter;

@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private final JwtTokenProvider jwtTokenProvider;
  private final CustomAuthenticationEntryPoint authenticationEntryPoint;
  private final MemberAuthenticationService memberAuthenticationService;

  @Override
  protected void doFilterInternal(
      HttpServletRequest request,
      HttpServletResponse response,
      FilterChain filterChain
  ) throws ServletException, IOException {
    String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);

    if (authorization == null) {
      filterChain.doFilter(request, response);
      return;
    }

    try {
      String accessToken = extractAccessToken(authorization);
      Claims claims = jwtTokenProvider.validateAccessToken(accessToken);

      Long memberId = Long.valueOf(claims.getSubject());
      MemberPrincipal principal = memberAuthenticationService.authenticate(memberId);

      var authentication = UsernamePasswordAuthenticationToken.authenticated(
          principal,
          null,
          List.of(new SimpleGrantedAuthority(principal.role().name()))
      );

      SecurityContext context = SecurityContextHolder.createEmptyContext();
      context.setAuthentication(authentication);
      SecurityContextHolder.setContext(context);
    } catch (JwtException | IllegalArgumentException | BadCredentialsException e) {
      SecurityContextHolder.clearContext();

      authenticationEntryPoint.commence(
          request,
          response,
          new BadCredentialsException("유효하지 않은 인증 토큰입니다.")
      );
      return;
    }

    filterChain.doFilter(request, response);
  }

  private String extractAccessToken(String authorization) {
    if (!authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
      throw new BadCredentialsException("지원하지 않는 인증 형식입니다.");
    }

    String token = authorization.substring(7);

    if (token.isBlank() || token.chars().anyMatch(Character::isWhitespace)) {
      throw new BadCredentialsException("인증 토큰 형식이 올바르지 않습니다.");
    }

    return token;
  }
}
