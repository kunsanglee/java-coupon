package coupon.quiz;


import static org.assertj.core.api.Assertions.assertThat;
import static coupon.quiz.QuizHelper.getCoupon;

import io.restassured.RestAssured;
import io.restassured.response.Response;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;
import org.apache.http.entity.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class MultipleIssueRequestsTest {

    private static final String BASE_URI = "http://localhost:8080";
    /**
     * 발급 수량 제한이 있는 쿠폰의 아이디
     */
    private static final Long ISSUE_LIMIT_COUPON_ID = 351159L;
    /**
     * 동시에 발급 요청하는 회원의 수
     */
    private static final int NUMBER_OF_MEMBERS = 10;
    /**
     * 회원당 발급 요청하는 쿠폰의 개수
     */
    private static final int COUPON_ISSUE_COUNT_PER_MEMBER = 20;

    @BeforeEach
    void setUp() {
        RestAssured.baseURI = BASE_URI;

        RestAssured.given()
                .header(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.getMimeType())
                .put("/coupons/initialize-issue-count/" + ISSUE_LIMIT_COUPON_ID);

        // select * from coupon c where c.id = 351159;
        // id가 351159L인 Coupon 데이터.
        // | #   | id      | coupon_status | created_at                  | discount_amount | issuable | issue_count | issue_ended_at           | issue_limit | issue_started_at         | limit_type    | minimum_order_price | modified_at            | usable | use_count | use_limit |
        //|-----|---------|---------------|-----------------------------|------------------|----------|-------------|--------------------------|-------------|--------------------------|----------------|---------------------|-------------------------|--------|-----------|-----------|
        //| 1   | 351159  | ISSUABLE      | 2024-07-29 17:02:55.000000 | 3500             | 1        | 0           | 2024-08-15 00:00:00.000000 | 150         | 2024-07-30 00:00:00.000000 | ISSUE_COUNT   | 10000               | 2024-08-05 17:02:55.000000 | 1      |           |           |
        // issue_limit : 150

        // @PutMapping("/coupons/initialize-issue-count/{couponId}")
        //    public void initializeIssueCount(@PathVariable("couponId") Long couponId) {
        //        couponRepository.updateIssueCount(couponId, 0L);
        //    }

        // @Modifying
        //    @Query("update Coupon c set c.issueCount = :issueCount where c.id = :couponId")
        //    void updateIssueCount(@Param("couponId") Long couponId, @Param("issueCount") Long issueCount);

        // @BeforeEach 에서 해당 쿠폰의 issue_count 를 0으로 초기화 한다.
    }

    @Test
    void 동시_발급_요청() throws InterruptedException {
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger requestCount = new AtomicInteger(0);

        ExecutorService executorService = Executors.newFixedThreadPool(NUMBER_OF_MEMBERS);
        for (int i = 1; i <= NUMBER_OF_MEMBERS; i++) {  // 회원 번호는 1부터 시작한다.
            int memberId = i;
            executorService.submit(() -> {
                issueCoupon(memberId, requestCount, successCount);
            });
        }
        executorService.shutdown();

        /*
         * Initiates an orderly shutdown in which previously submitted
         * tasks are executed, but no new tasks will be accepted.
         * Invocation has no additional effect if already shut down.
         *
         * <p>This method does not wait for previously submitted tasks to
         * complete execution.  Use {@link #awaitTermination awaitTermination}
         * to do that.
         *
         * @throws SecurityException if a security manager exists and
         *         shutting down this ExecutorService may manipulate
         *         threads that the caller is not permitted to modify
         *         because it does not hold {@link
         *         java.lang.RuntimePermission}{@code ("modifyThread")},
         *         or the security manager's {@code checkAccess} method
         *         denies access.
        void shutdown();
        메서드 설명: shutdown()
        목적:

        shutdown() 메서드는 ExecutorService를 순차적으로 종료하는 기능을 제공합니다.
        이 메서드를 호출하면 더 이상 새로운 작업을 받을 수 없지만, 이미 제출된 작업은 실행됩니다.
        작동 방식:

        호출 후, 이미 제출된 작업들은 계속 실행되지만 새로운 작업은 더 이상 수락되지 않습니다.
        이 메서드는 호출되더라도 이미 종료된 경우에는 추가적인 효과가 없습니다.

        비동기 실행:
        shutdown() 메서드는 이전에 제출된 작업들이 완료될 때까지 기다리지 않습니다.
        작업 완료를 기다리려면 awaitTermination() 메서드를 사용해야 합니다.

        예외:
        SecurityException이 발생할 수 있습니다.
        이는 보안 관리자(Security Manager)가 존재할 때, 이 ExecutorService가 스레드를 조작하려고 하는데,
        호출자가 해당 스레드를 수정할 수 있는 권한이 없을 경우 발생합니다.
        이 경우, modifyThread라는 RuntimePermission을 요구합니다.

        요약
        shutdown() 메서드는 ExecutorService를 종료하는데 사용되며, 기존 작업은 실행되지만 새로운 작업은 수락하지 않습니다.
        작업 완료를 기다리려면 별도의 메서드를 사용해야 하며, 보안 문제가 있을 경우 SecurityException이 발생할 수 있습니다.
         */
        executorService.awaitTermination(30, TimeUnit.SECONDS);

        assertThat(successCount.get()).isEqualTo(150);
        assertThat(requestCount.get()).isEqualTo(200);

        Response couponResponse = getCoupon(ISSUE_LIMIT_COUPON_ID);
        long issueCount = couponResponse.body().jsonPath().getLong("issueCount");
        assertThat(issueCount).isEqualTo(150);
    }

    private static void issueCoupon(int memberId, AtomicInteger requestCount, AtomicInteger successCount) {
        for (int count = 0; count < COUPON_ISSUE_COUNT_PER_MEMBER; count++) {
            String requestBody = "{ \"couponId\": " + ISSUE_LIMIT_COUPON_ID + ", \"memberId\": " + memberId + " }";
            Response response = RestAssured.given()
                    .header(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.getMimeType())
                    .body(requestBody)
                    .post("/member-coupons")
                    .then()
                    .extract().response();

            requestCount.incrementAndGet();

            if (response.getStatusCode() == HttpStatus.SC_OK) {
                successCount.incrementAndGet();
            }
        }
    }

}
