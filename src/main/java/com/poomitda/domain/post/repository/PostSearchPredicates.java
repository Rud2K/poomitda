package com.poomitda.domain.post.repository;

import com.poomitda.domain.post.dto.PostRequestDto.PostListRequest;
import com.poomitda.domain.post.entity.Post;
import jakarta.persistence.criteria.*;
import java.time.LocalDate;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class PostSearchPredicates {

  /**
   * 삭제 여부와 값 일치 방식의 공통 검색 조건을 생성한다.
   * 상태, 나이, 검색어, 커서 조건은 포함하지 않는다.
   *
   * @param cb 조회 조건을 생성할 {@link CriteriaBuilder}
   * @param post 현재 조회의 공고 엔티티 경로
   * @param request 검증과 입력값 정리가 완료된 검색 요청
   * @return 현재 조회에서 사용할 검색 조건을 담은 {@link List}
   */
  public List<Predicate> createBasicPredicates(
      CriteriaBuilder cb,
      Root<Post> post,
      PostListRequest request
  ) {
    List<Predicate> predicates = new ArrayList<>();

    // 삭제되지 않은 공고만 조회
    predicates.add(cb.isNull(post.get("deletedAt")));

    // noticeType이 전달됐으면 일치 조건 추가
    if (request.noticeType() != null) {
      predicates.add(cb.equal(post.get("noticeType"), request.noticeType()));
    }

    // species가 전달됐으면 일치 조건 추가
    if (request.species() != null) {
      predicates.add(cb.equal(post.get("species"), request.species()));
    }

    // gender가 전달됐으면 일치 조건 추가
    if (request.gender() != null) {
      predicates.add(cb.equal(post.get("gender"), request.gender()));
    }

    // region이 전달됐으면 일치 조건 추가
    if (request.region() != null) {
      predicates.add(cb.equal(post.get("region"), request.region()));
    }

    // district가 전달됐으면 일치 조건 추가
    if (request.district() != null) {
      predicates.add(cb.equal(post.get("district"), request.district()));
    }

    return predicates;
  }

  /**
   * 제목 또는 본문에 검색어가 포함되는 조건을 생성한다.
   * 검색어의 %, _, !는 일반 문자로 취급한다.
   *
   * @param cb 조회 조건을 생성할 {@link CriteriaBuilder}
   * @param post 현재 조회의 공고 엔티티 경로
   * @param keyword 검증과 앞뒤 공백 제거가 완료된 검색어
   * @return 검색어가 없으면 항상 참인 조건, 있으면 제목 또는 본문 검색 조건
   */
  public Predicate createKeywordPredicate(
      CriteriaBuilder cb,
      Root<Post> post,
      String keyword
  ) {
    if (keyword == null) {
      return cb.conjunction();
    }

    String escapedKeyword = keyword
        .replace("!", "!!")
        .replace("%", "!%")
        .replace("_", "!_");

    String pattern = "%" + escapedKeyword + "%";

    return cb.or(
        cb.like(post.<String>get("title"), pattern, '!'),
        cb.like(post.<String>get("content"), pattern, '!')
    );
  }

  /**
   * 기준일의 만 개월 수에 따른 나이 범위 조건을 생성한다.
   * 나이 필터가 있으면 출생일 미상과 기준일 이후 출생일은 제외한다.
   *
   * @param cb 조회 조건을 생성할 {@link CriteriaBuilder}
   * @param post 현재 조회의 공고 엔티티 경로
   * @param request 나이 범위 검증이 완료된 검색 요청
   * @param referenceDate 나이 계산 기준일
   * @return 나이 필터가 없으면 항상 참인 조건, 있으면 나이 범위 조건
   */
  public Predicate createAgePredicate(
      CriteriaBuilder cb,
      Root<Post> post,
      PostListRequest request,
      LocalDate referenceDate
  ) {
    Integer minAgeMonths = request.minAgeMonths();
    Integer maxAgeMonths = request.maxAgeMonths();

    if (minAgeMonths == null && maxAgeMonths == null) {
      return cb.conjunction();
    }

    if (referenceDate == null) {
      throw new IllegalArgumentException("나이 검색 조건의 기준일은 null일 수 없습니다.");
    }

    Path<LocalDate> birthDate = post.get("estimatedBirthDate");

    Expression<Integer> birthYear = cb.function("year", Integer.class, birthDate);
    Expression<Integer> birthMonth = cb.function("month", Integer.class, birthDate);
    Expression<Integer> birthDay = cb.function("day", Integer.class, birthDate);

    // 연도 차이를 개월 수로 변환
    Expression<Integer> yearMonths = cb.prod(
        cb.diff(cb.literal(referenceDate.getYear()), birthYear),
        12
    );

    // 연도와 월만으로 계산한 개월 수
    Expression<Integer> totalMonths = cb.sum(
        yearMonths,
        cb.diff(cb.literal(referenceDate.getMonthValue()), birthMonth)
    );

    // 기준일의 일이 출생일의 일보다 작으면 한 달을 차감
    Expression<Integer> adjustment = cb.<Integer>selectCase()
        .when(cb.gt(birthDay, referenceDate.getDayOfMonth()), 1)
        .otherwise(0);

    Expression<Integer> ageMonths = cb.diff(totalMonths, adjustment);

    List<Predicate> predicates = new ArrayList<>();
    predicates.add(cb.isNotNull(birthDate));
    predicates.add(cb.lessThanOrEqualTo(birthDate, referenceDate));

    if (minAgeMonths != null) {
      predicates.add(cb.ge(ageMonths, minAgeMonths));
    }

    if (maxAgeMonths != null) {
      predicates.add(cb.le(ageMonths, maxAgeMonths));
    }

    return cb.and(predicates.toArray(new Predicate[0]));
  }
}
