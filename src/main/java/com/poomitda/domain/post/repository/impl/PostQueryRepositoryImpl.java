package com.poomitda.domain.post.repository.impl;

import com.poomitda.domain.post.cursor.PostCursor;
import com.poomitda.domain.post.dto.PostRequestDto.PostListRequest;
import com.poomitda.domain.post.dto.constant.*;
import com.poomitda.domain.post.entity.Post;
import com.poomitda.domain.post.entity.constant.PostStatus;
import com.poomitda.domain.post.repository.*;
import jakarta.persistence.*;
import jakarta.persistence.criteria.*;
import java.time.*;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class PostQueryRepositoryImpl implements PostQueryRepository {

  @PersistenceContext
  private EntityManager entityManager;

  private final PostSearchPredicates searchPredicates;

  /**
   * 검색 조건과 커서에 따라 삭제되지 않은 공고 목록을 조회한다.
   * 호출 전에 검색 범위의 필요한 마감 상태 갱신이 완료되어야 한다.
   *
   * @param request 검증과 입력값 정리가 완료된 검색 요청
   * @param cursor 검증된 커서. 첫 페이지 조회에서는 null
   * @param referenceDate 나이 계산 기준일. 다음 페이지에서도 동일한 날짜를 사용
   * @param now 현재 요청에서 마감 여부를 판단할 UTC 기준 시각
   * @return 정렬 조건에 맞는 공고를 최대 size + 1개 담은 {@link List}
   */
  @Override
  public List<Post> findPosts(
      PostListRequest request,
      PostCursor cursor,
      LocalDate referenceDate,
      LocalDateTime now
  ) {
    if (request == null || referenceDate == null || now == null) {
      throw new IllegalArgumentException(
          "공고 목록 조회에는 검색 요청, 나이 기준일, 마감 시각 기준이 필요합니다."
      );
    }

    Integer size = request.size();
    if (size == null || size < 1 || size > 50) {
      throw new IllegalArgumentException(
          "공고 목록 조회 개수는 1 이상 50 이하여야 합니다."
      );
    }

    CriteriaBuilder cb = entityManager.getCriteriaBuilder();
    CriteriaQuery<Post> query = cb.createQuery(Post.class);
    Root<Post> post = query.from(Post.class);

    List<Predicate> predicates = searchPredicates.createBasicPredicates(
        cb,
        post,
        request
    );

    predicates.add(searchPredicates.createKeywordPredicate(
        cb,
        post,
        request.keyword()
    ));

    predicates.add(searchPredicates.createAgePredicate(
        cb,
        post,
        request,
        referenceDate
    ));

    predicates.add(createStatusPredicate(
        cb,
        post,
        request.status(),
        now
    ));

    predicates.add(createSortPredicate(
        cb,
        post,
        request.sort()
    ));

    predicates.add(createCursorPredicate(
        cb,
        post,
        cursor
    ));

    query.select(post)
        .where(predicates.toArray(new Predicate[0]))
        .orderBy(createOrders(cb, post, request.sort()));

    return entityManager.createQuery(query)
        .setMaxResults(size + 1)
        .getResultList();
  }

  /**
   * 검색 범위 안에서 마감 상태를 확인할 미종료 공고 ID를 조회한다.
   * 요청의 상태 필터와 페이지 커서는 적용하지 않는다.
   *
   * @param request 검증과 입력값 정리가 완료된 검색 요청
   * @param referenceDate 나이 계산 기준일
   * @param now 마감 대상 여부를 판단할 UTC 기준 시각
   * @param lastPostId 직전 묶음의 마지막 공고 ID. 최초 조회에서는 0
   * @param batchSize 한 번에 조회할 최대 개수. 양수여야 함
   * @return 대상 공고 ID를 오름차순으로 담은 {@link List}
   */
  @Override
  public List<Long> findDeadlineTargetIdsForSearch(
      PostListRequest request,
      LocalDate referenceDate,
      LocalDateTime now,
      Long lastPostId,
      int batchSize
  ) {
    if (request == null || referenceDate == null || now == null) {
      throw new IllegalArgumentException(
          "마감 대상 조회에는 검색 요청, 나이 기준일, 마감 기준 시각이 필요합니다."
      );
    }

    if (lastPostId == null || lastPostId < 0) {
      throw new IllegalArgumentException(
          "마감 대상 조회의 마지막 공고 ID는 0 이상이어야 합니다."
      );
    }

    if (batchSize <= 0) {
      throw new IllegalArgumentException(
          "마감 대상 조회 개수는 양수여야 합니다."
      );
    }

    CriteriaBuilder cb = entityManager.getCriteriaBuilder();
    CriteriaQuery<Long> query = cb.createQuery(Long.class);
    Root<Post> post = query.from(Post.class);

    List<Predicate> predicates = searchPredicates.createBasicPredicates(
        cb,
        post,
        request
    );

    predicates.add(searchPredicates.createKeywordPredicate(
        cb,
        post,
        request.keyword()
    ));

    predicates.add(searchPredicates.createAgePredicate(
        cb,
        post,
        request,
        referenceDate
    ));

    // 아직 모집 종료되지 않은 공고
    predicates.add(
        post.get("status").in(
            PostStatus.RECRUITING,
            PostStatus.RECEPTION_CLOSED
        )
    );

    // 기준 시각에 마감된 공고
    Path<LocalDateTime> deadline = post.get("recruitmentDeadline");
    predicates.add(cb.isNotNull(deadline));
    predicates.add(cb.lessThanOrEqualTo(deadline, now));

    // 이전 묶음 이후의 공고
    Path<Long> postId = post.get("postId");
    predicates.add(cb.greaterThan(postId, lastPostId));

    query.select(postId)
        .where(predicates.toArray(new Predicate[0]))
        .orderBy(cb.asc(postId));

    return entityManager.createQuery(query)
        .setMaxResults(batchSize)
        .getResultList();
  }

  /**
   * 마감 상태 갱신 이후 목록 조회에 사용할 상태 조건을 생성한다.
   *
   * @param cb 조회 조건을 생성할 {@link CriteriaBuilder}
   * @param post 현재 조회의 공고 엔티티 경로
   * @param status 조회할 공고 상태 필터
   * @param now 접수 마감 여부를 판단할 UTC 기준 시각
   * @return 상태 필터에 해당하는 조회 조건
   */
  private Predicate createStatusPredicate(
      CriteriaBuilder cb,
      Root<Post> post,
      PostStatusFilter status,
      LocalDateTime now
  ) {
    if (status == null || now == null) {
      throw new IllegalArgumentException(
          "공고 상태 검색에는 상태 필터와 마감 기준 시각이 필요합니다."
      );
    }

    Path<PostStatus> postStatus = post.get("status");
    Path<LocalDateTime> deadline = post.get("recruitmentDeadline");

    return switch (status) {
      case PostStatusFilter.RECRUITING -> cb.and(
          cb.equal(postStatus, PostStatus.RECRUITING),
          cb.or(
              cb.isNull(deadline),
              cb.greaterThan(deadline, now)
          )
      );

      case PostStatusFilter.RECEPTION_CLOSED -> cb.equal(postStatus, PostStatus.RECEPTION_CLOSED);

      case PostStatusFilter.CLOSED -> cb.equal(postStatus, PostStatus.CLOSED);

      case PostStatusFilter.ALL -> cb.conjunction();
    };
  }

  /**
   * 정렬 기준값과 공고 ID를 사용해 다음 페이지 조회 조건을 생성한다.
   *
   * @param cb 조회 조건을 생성할 {@link CriteriaBuilder}
   * @param post 현재 조회의 공고 엔티티 경로
   * @param cursor 현재 요청과의 일치 여부 및 필수값 검증이 완료된 커서
   * @return 첫 페이지이면 항상 참인 조건, 아니면 커서 이후 조회 조건
   */
  private Predicate createCursorPredicate(
      CriteriaBuilder cb,
      Root<Post> post,
      PostCursor cursor
  ) {
    if (cursor == null) {
      return cb.conjunction();
    }

    Path<Long> postId = post.get("postId");

    return switch (cursor.sort()) {
      case PostSort.LATEST -> {
        Path<LocalDateTime> createdAt = post.get("createdAt");
        yield cb.or(
            cb.lessThan(createdAt, cursor.createdAt()),
            cb.and(
                cb.equal(createdAt, cursor.createdAt()),
                cb.lessThan(postId, cursor.postId())
            )
        );
      }

      case PostSort.BOOKMARK -> {
        Path<Integer> bookmarkCount = post.get("bookmarkCount");
        yield cb.or(
            cb.lessThan(bookmarkCount, cursor.bookmarkCount()),
            cb.and(
                cb.equal(bookmarkCount, cursor.bookmarkCount()),
                cb.lessThan(postId, cursor.postId())
            )
        );
      }

      case PostSort.CLOSING -> {
        Path<LocalDateTime> deadline = post.get("recruitmentDeadline");
        yield cb.or(
            cb.greaterThan(deadline, cursor.recruitmentDeadline()),
            cb.and(
                cb.equal(deadline, cursor.recruitmentDeadline()),
                cb.greaterThan(postId, cursor.postId())
            )
        );
      }
    };
  }

  /**
   * 목록 조회의 정렬 기준과 동률 처리 순서를 생성한다.
   *
   * @param cb 정렬 조건을 생성할 {@link CriteriaBuilder}
   * @param post 현재 조회의 공고 엔티티 경로
   * @param sort 목록 정렬 방식
   * @return 정렬 우선순위대로 구성한 {@link Order} 목록
   */
  private List<Order> createOrders(
      CriteriaBuilder cb,
      Root<Post> post,
      PostSort sort
  ) {
    if (sort == null) {
      throw new IllegalArgumentException(
          "공고 목록 정렬 조건은 null일 수 없습니다."
      );
    }

    return switch (sort) {
      case PostSort.LATEST -> List.of(
          cb.desc(post.get("createdAt")),
          cb.desc(post.get("postId"))
      );

      case PostSort.BOOKMARK -> List.of(
          cb.desc(post.get("bookmarkCount")),
          cb.desc(post.get("postId"))
      );

      case PostSort.CLOSING -> List.of(
          cb.asc(post.get("recruitmentDeadline")),
          cb.asc(post.get("postId"))
      );
    };
  }

  /**
   * 정렬 방식에 따른 조회 대상 제한 조건을 생성한다.
   *
   * @param cb 조회 조건을 생성할 {@link CriteriaBuilder}
   * @param post 현재 조회의 공고 엔티티 경로
   * @param sort 목록 정렬 방식
   * @return 마감임박순이면 마감 시각 존재 조건, 그 외에는 항상 참인 조건
   */
  private Predicate createSortPredicate(
      CriteriaBuilder cb,
      Root<Post> post,
      PostSort sort
  ) {
    if (sort == null) {
      throw new IllegalArgumentException(
          "공고 목록 정렬 조건은 null일 수 없습니다."
      );
    }

    if (sort == PostSort.CLOSING) {
      return cb.isNotNull(post.get("recruitmentDeadline"));
    }

    return cb.conjunction();
  }
}
