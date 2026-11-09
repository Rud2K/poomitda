package com.poomitda.domain.post.scheduler;

import com.poomitda.domain.post.service.PostDeadlineBatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    prefix = "post.deadline",
    name = "enabled",
    havingValue = "true"
)
public class PostDeadlineScheduler {

  private final PostDeadlineBatchService postDeadlineBatchService;

  @Scheduled(
      initialDelayString = "${post.deadline.initial-delay-ms}",
      fixedDelayString = "${post.deadline.fixed-delay-ms}"
  )
  public void updateExpiredPosts() {
    try {
      postDeadlineBatchService.updateExpiredPosts();
    } catch (RuntimeException exception) {
      log.error("공고 마감 처리 배치 실행에 실패했습니다.", exception);
    }
  }
}
