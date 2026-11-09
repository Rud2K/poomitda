package com.poomitda.domain.post.service;

import com.poomitda.domain.matching.entity.constant.ApplicationStatus;
import com.poomitda.domain.matching.repository.ApplicationRepository;
import com.poomitda.domain.post.entity.Post;
import com.poomitda.domain.post.entity.constant.*;
import com.poomitda.domain.post.exception.PostErrorCode;
import com.poomitda.domain.post.repository.PostRepository;
import com.poomitda.global.exception.exception.BusinessException;
import java.time.*;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PostDeadlineService {

  private final PostRepository postRepository;
  private final ApplicationRepository applicationRepository;

  /**
   * 공고를 잠금 조회하고 마감 시각과 신청 상태에 따라 접수 또는 모집을 종료한다.
   * 이미 종료된 공고의 종료 사유와 종료 시각은 유지한다.
   *
   * @param postId 마감 상태를 갱신할 공고 ID
   * @throws BusinessException 공고가 존재하지 않거나 삭제된 경우
   * @throws IllegalStateException 마감된 미종료 공고에 승인 또는 완료된 신청이 있는 경우
   */
  @Transactional
  public void updateDeadlineStatus(Long postId) {
    // 삭제되지 않은 공고를 잠금 조회
    Post post = postRepository.findByIdForUpdate(postId)
        .orElseThrow(
            () -> new BusinessException(PostErrorCode.POST_NOT_FOUND)
        );

    // post의 상태가 CLOSED라면 종료
    if (post.getStatus() == PostStatus.CLOSED) {
      return;
    }

    // 잠금을 획득한 뒤 현재 UTC 시각을 한 번 구함
    LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

    // post.isRecruitmentDeadlineReached(now)가 false라면 종료
    if (!post.isRecruitmentDeadlineReached(now)) {
      return;
    }

    // APPROVED 또는 COMPLETED 신청이 존재하는지 조회
    boolean hasSelectedApplication = applicationRepository.existsByPost_PostIdAndStatusIn(
        postId,
        List.of(
            ApplicationStatus.APPROVED,
            ApplicationStatus.COMPLETED
        )
    );

    // hasSelectedApplication이 true라면 IllegalStateException 발생
    if (hasSelectedApplication) {
      throw new IllegalStateException("종료되지 않은 공고에 승인 또는 완료된 신청이 존재합니다.");
    }

    // APPLIED 또는 UNDER_REVIEW 신청이 존재하는지 조회
    boolean hasPendingApplication = applicationRepository.existsByPost_PostIdAndStatusIn(
        postId,
        List.of(
            ApplicationStatus.APPLIED,
            ApplicationStatus.UNDER_REVIEW
        )
    );

    // hasPendingApplication이 true라면 post.closeReception() 호출 후 종료
    if (hasPendingApplication) {
      post.closeReception();
      return;
    }

    // 상태와 관계없이 신청 이력이 존재하는지 조회
    boolean hasApplicationHistory = applicationRepository.existsByPost_PostId(postId);

    // 신청 이력에 따라 종료 사유 결정
    PostClosedReason reason = hasApplicationHistory
        ? PostClosedReason.NO_SELECTION
        : PostClosedReason.NO_APPLICANTS;

    // post.closeAfterDeadline(reason, now) 호출
    post.closeAfterDeadline(reason, now);
  }
}
