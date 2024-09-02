package coupon.quiz;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import com.jayway.jsonpath.internal.function.numeric.Average;
import io.restassured.RestAssured;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class IndexPerformanceTest {

    private static final String BASE_URI = "http://localhost:8080";
    private static final Long MIN_COUPON_ID = 1L;
    private static final Long MAX_COUPON_ID = 351160L;
    private static final Long MIN_MEMBER_ID = 1L;
    private static final Long MAX_MEMBER_ID = 250000L;
    private static final int THREAD_COUNT = 10;
    private static final int TEST_DURATION_SECONDS = 10;
    private static final long MILLISECONDS_IN_SECOND = 1000L;

    @BeforeEach
    void setUp() {
        RestAssured.baseURI = BASE_URI;
    }

    @Test
    void 쿠폰의_발급_수량_조회() throws InterruptedException {
        AtomicBoolean running = new AtomicBoolean(false);
        AtomicInteger requestCount = new AtomicInteger(0);
        AtomicLong totalElapsedTime = new AtomicLong(0);

        int statusCode = RestAssured.get("/coupons/" + ThreadLocalRandom.current()
                .nextLong(MIN_COUPON_ID, MAX_COUPON_ID + 1) + "/issued-count").statusCode();
        assertThat(statusCode).withFailMessage("쿠폰의 발급 수량 조회 API 호출에 실패했습니다. 테스트 대상 서버가 실행중인지 확인해 주세요.").isEqualTo(200);

        executeMultipleRequests(running, requestCount, totalElapsedTime,
                () -> RestAssured.get("/coupons/" + ThreadLocalRandom.current()
                        .nextLong(MIN_COUPON_ID, MAX_COUPON_ID + 1) + "/issued-count"));

        System.out.println("Total request count: " + requestCount.get());
        System.out.println("Total elapsed time: " + totalElapsedTime.get() + "ms");

        long averageElapsedTime = totalElapsedTime.get() / requestCount.get();
        System.out.println("Average elapsed time: " + averageElapsedTime + "ms");

        assertThat(averageElapsedTime).isLessThanOrEqualTo(100L);
        //Long countByCoupon_Id(Long couponId);

        // select count(*)
        // from member_coupon mc
        // where mc.coupon_id = couponId;

        // Coupon : MemberCoupon = 1:N @ManyToOne 관계.
        // MemberCoupon 은 Coupon 의 id를 FK 로 가지고 있다.
        // 그래서 show index from member_coupon; 조회를 해보면,
        // 외래 키 제약 조건(FK Constraints) 설정시 외래 키 컬럼에 대한 인덱스를 자동으로 생성한다.
        // 그래서 따로 인덱스를 설정해주지 않아도 통과.

        //Total request count: 12261
        //Total elapsed time: 100029ms
        //Average elapsed time: 8ms
    }

    @Test
    void 쿠폰의_사용_수량_조회() throws InterruptedException {
        AtomicBoolean running = new AtomicBoolean(false);
        AtomicInteger requestCount = new AtomicInteger(0);
        AtomicLong totalElapsedTime = new AtomicLong(0);

        int statusCode = RestAssured.get("/coupons/" + ThreadLocalRandom.current()
                .nextLong(MIN_COUPON_ID, MAX_COUPON_ID + 1) + "/used-count").statusCode();
        assertThat(statusCode).withFailMessage("쿠폰의 사용 수량 조회 API 호출에 실패했습니다. 테스트 대상 서버가 실행중인지 확인해 주세요.").isEqualTo(200);

        executeMultipleRequests(running, requestCount, totalElapsedTime,
                () -> RestAssured.get("/coupons/" + ThreadLocalRandom.current()
                        .nextLong(MIN_COUPON_ID, MAX_COUPON_ID + 1) + "/used-count"));

        System.out.println("Total request count: " + requestCount.get());
        System.out.println("Total elapsed time: " + totalElapsedTime.get() + "ms");

        long averageElapsedTime = totalElapsedTime.get() / requestCount.get();
        System.out.println("Average elapsed time: " + totalElapsedTime.get() / requestCount.get() + "ms");

        assertThat(averageElapsedTime).isLessThanOrEqualTo(100L);
        // Long countByCoupon_IdAndUsed(Long couponId, boolean used);

        // select count(*)
        // from MemberCoupon mc
        // where mc.coupon_id = couponId
        // and mc.used = used;

        // MemberCoupon : Coupon = N : 1 관계 이므로,
        // MemberCoupon 은 Coupon 의 id를 FK 로 가지고 있고, 인덱스 또한 자동으로 생성되어 있다.
        // member_coupon 테이블이 가지고 있는 coupon_id fk 제약조건을 지우고 인덱스 또한 삭제하고 실행해 봤다.
        // fk coupon_id 인덱스 없을 때
        //Total request count: 872
        //Total elapsed time: 100555ms
        //Average elapsed time: 115ms

        // fk coupon_id 인덱스만 있을 때
        //Total request count: 11456
        //Total elapsed time: 100032ms
        //Average elapsed time: 8ms

        // idx_coupon_used
        // coupon_id, member_coupon.used 컬럼 순서로 인덱스
        //Total request count: 12920
        //Total elapsed time: 100051ms
        //Average elapsed time: 7ms

        // idx_used_coupon 순서로 인덱스
        // used - cardinality : 2505
        // coupon_id - cardinality : 1344
        // 그래서 used, coupon_id 순서로 인덱스를 변경 후 실행
        //Total request count: 11494
        //Total elapsed time: 99948ms
        //Average elapsed time: 8ms

        // 세 결과를 비교해보니 큰 차이가 발생하지 않았다. 또 used는 boolean 필드라서 true, false밖에 값이 없는데,
        // 어떻게 cardinality가 coupon_id보다 높았았을까?

         /*GPT 설명
         Cardinality 정의:
         Cardinality는 특정 컬럼의 고유한 값의 수를 나타냅니다.
         used 컬럼이 boolean 타입이라면, 값이 true와 false 두 가지밖에 없으므로, 이론적으로는 cardinality가 2여야 합니다.
         그러나 인덱스 통계에 따라 cardinality가 높게 나타날 수 있습니다.

         데이터 분포:
         used 컬럼의 값이 특정 값에 편중되어 있지 않고,
         데이터베이스에 저장된 레코드에서 true와 false가 비교적 균형 있게 분포되어 있을 경우,
         DBMS는 이를 바탕으로 cardinality를 계산합니다.
         예를 들어, true와 false가 각각 많은 수의 레코드에 걸쳐 존재하면, cardinality가 더 높게 평가될 수 있습니다.

         인덱스 통계 갱신:
         인덱스 통계는 정기적으로 갱신되지 않거나, 데이터 변경이 많았던 경우 이전 통계가 남아 있을 수 있습니다.
         이로 인해 실제 데이터와 통계 간의 불일치가 발생할 수 있습니다.

         ---

         used 컬럼의 cardinality가 더 높음에도 불구하고
         외래 키인 coupon_id의 인덱스 순서를 먼저 두는 이유:

         쿼리의 필터링 조건:
         일반적으로 외래 키는 데이터베이스에서 자주 사용되는 필터링 조건입니다.
         coupon_id가 쿼리에서 직접적으로 사용되는 경우가 많기 때문에, 인덱스의 첫 번째 컬럼으로 두는 것이 효율적입니다

         조인 성능:
         외래 키는 일반적으로 다른 테이블과의 조인에서 사용됩니다.
         coupon_id가 조인 조건으로 사용될 경우, 인덱스의 첫 번째 컬럼으로 위치하는 것이 조인 성능을 향상시킬 수 있습니다.

         데이터 선택성:
         used가 boolean 필드로서 두 가지 값만 가지므로, 인덱스에서의 선택성이 떨어질 수 있습니다.
         반면, coupon_id는 더 다양한 값을 가질 수 있어 데이터 선택성이 높습니다.
         선택성이 높은 컬럼을 인덱스의 앞부분에 두는 것이 쿼리 성능에 긍정적인 영향을 미칩니다.
         */
    }

    @Test
    void 현재_발급_가능한_쿠폰_조회() throws InterruptedException {
        AtomicBoolean running = new AtomicBoolean(false);
        AtomicInteger requestCount = new AtomicInteger(0);
        AtomicLong totalElapsedTime = new AtomicLong(0);

        int statusCode = RestAssured.get("/coupons/issuable").statusCode();
        assertThat(statusCode).withFailMessage("발급 가능한 쿠폰 조회 API 호출에 실패했습니다. 테스트 대상 서버가 실행중인지 확인해 주세요.").isEqualTo(200);

        executeMultipleRequests(running, requestCount, totalElapsedTime, () -> RestAssured.get("/coupons/issuable"));

        System.out.println("Total request count: " + requestCount.get());
        System.out.println("Total elapsed time: " + totalElapsedTime.get() + "ms");

        long averageElapsedTime = totalElapsedTime.get() / requestCount.get();
        System.out.println("Average elapsed time: " + totalElapsedTime.get() / requestCount.get() + "ms");

        assertThat(averageElapsedTime).isLessThanOrEqualTo(500L);
        // before index
        //Total request count: 456
        //Total elapsed time: 101161ms
        //Average elapsed time: 221ms

        // List<Coupon> findAllByIssuableAndCouponStatusAndIssueStartedAtLessThanAndIssueEndedAtGreaterThan(boolean issuable,
        //                                                                                                     CouponStatus couponStatus,
        //                                                                                                     LocalDateTime issueStartedAt,
        //                                                                                                     LocalDateTime issueEndedAt);
        // 순서에 맞게 인덱스 생성.
        // create index idx_issuable_coupon_status_issue_started_at_issue_ended_at ON coupon (issuable, coupon_status, issue_started_at, issue_ended_at);
        //Total request count: 11478
        //Total elapsed time: 100044ms
        //Average elapsed time: 8ms

        // List<Coupon> findAllByIssueStartedAtLessThanAndIssueEndedAtGreaterThanAndCouponStatusAndIssuable(LocalDateTime issueStartedAt,
        //                                                                                                     LocalDateTime issueEndedAt,
        //                                                                                                     CouponStatus couponStatus,
        //                                                                                                     boolean issuable);
        // 순서에 맞게 인덱스 생성.
        // create index idx_issue_started_at_issue_ended_at_coupon_status_issuable ON coupon (issue_started_at, issue_ended_at, coupon_status, issuable);
        //Total request count: 454
        //Total elapsed time: 100920ms
        //Average elapsed time: 222ms

        // 왜 두 인덱스의 성능 차이가 이렇게 심할까?
        // 심지어 두 번째 인덱스는 cardinality가 높은 issue_started_at, issue_ended_at, coupon_status, issuable 순서로 인덱스를 걸었는데?

        /*
        4. 인덱스 조회시 주의 사항
        between, like, <, > 등 범위 조건은 해당 컬럼은 인덱스를 타지만, 그 뒤 인덱스 컬럼들은 인덱스가 사용되지 않습니다.
        즉, group_no, from_date, is_bonus으로 인덱스가 잡혀있을 때
        조회 쿼리를 where group_no=XX and is_bonus=YY and from_date > ZZ등으로 잡으면 is_bonus는 인덱스가 사용되지 않습니다.
        범위조건으로 사용하면 안된다고 기억하시면 좀 더 쉽습니다.

        반대로 =, in 은 다음 컬럼도 인덱스를 사용합니다.
        in은 결국 =를 여러번 실행시킨 것이기 때문입니다.
        단, in은 인자값으로 상수가 포함되면 문제 없지만, 서브쿼리를 넣게되면 성능상 이슈가 발생합니다.
        in의 인자로 서브쿼리가 들어가면 서브쿼리의 외부가 먼저 실행되고, in 은 체크조건으로 실행되기 때문입니다.
        AND연산자는 각 조건들이 읽어와야할 ROW수를 줄이는 역할을 하지만,
        or 연산자는 비교해야할 ROW가 더 늘어나기 때문에 풀 테이블 스캔이 발생할 확률이 높습니다.
        WHERE 에서 OR을 사용할때는 주의가 필요합니다.

        인덱스로 사용된 컬럼값 그대로 사용해야만 인덱스가 사용됩니다.
        인덱스는 가공된 데이터를 저장하고 있지 않습니다.
        where salary * 10 > 150000;는 인덱스를 못타지만,
        where salary > 150000 / 10; 은 인덱스를 사용합니다.

        컬럼이 문자열인데 숫자로 조회하면 타입이 달라 인덱스가 사용되지 않습니다. 정확한 타입을 사용해야만 합니다.
        null 값의 경우 is null 조건으로 인덱스 레인지 스캔 가능

        5. 인덱스 컬럼 순서와 조회 컬럼 순서
        최근엔 이전과 같이 꼭 인덱스 순서와 조회 순서를 지킬 필요는 없습니다.
        인덱스 컬럼들이 조회조건에 포함되어 있는지가 중요합니다.

        조회컬럼과인덱스순서

        (3-1 실험과 동일한 인덱스에 조회 순서만 변경해서 실행한 결과)

        보시는것처럼 조회 컬럼의 순서는 인덱스에 큰 영향을 끼치지 못합니다.
        단, 옵티마이저가 조회 조건의 컬럼을 인덱스 컬럼 순서에 맞춰 재배열하는 과정이 추가되지만 거의 차이가 없긴 합니다.
        (그래도 이왕이면 맞추는게 조금이나마 낫겠죠?)
         */

        /*
        1. 인덱스 순서의 중요성
        첫 번째 인덱스:
        idx_issuable_coupon_status_issue_started_at_issue_ended_at (issuable, coupon_status, issue_started_at, issue_ended_at)는
        issuable과 coupon_status 조건이 먼저 옵니다.
        이 경우, 두 조건이 필터링된 후 issue_started_at과 issue_ended_at으로 범위를 제한합니다.
        이 인덱스는 주로 issuable과 coupon_status가 자주 필터링되는 경우에 유리합니다.

        두 번째 인덱스:
        idx_issue_started_at_issue_ended_at_coupon_status_issuable (issue_started_at, issue_ended_at, coupon_status, issuable)는
        시간 기준으로 먼저 필터링됩니다.
        이 인덱스는 issue_started_at과 issue_ended_at의 범위 조건을 먼저 평가하고,
        그 후에 카디널리티가 높은 coupon_status와 issuable을 평가합니다.

        2. 카디널리티와 데이터 분포
        카디널리티:
        두 번째 인덱스가 카디널리티를 잘 고려했다고 하더라도,
        쿼리에서 사용하는 조건이 issue_startedAtLessThan과 issueEndedAtGreaterThan의 조합이기 때문에,
        인덱스가 범위 검색을 수행할 때 성능이 저하될 수 있습니다.
        범위 검색은 인덱스의 효율성을 떨어뜨릴 수 있습니다.

        3. 쿼리 실행 계획
        실행 계획 차이:
        EXPLAIN 명령어를 사용하여 두 쿼리의 실행 계획을 비교해보는 것이 중요합니다.
        이 계획을 통해 인덱스가 어떻게 사용되고 있는지, 어떤 조건이 성능에 영향을 미치는지 알 수 있습니다.

        4. 요청 수와 데이터 양
        요청 수: 첫 번째 쿼리는 11,478건의 요청에 대해 평균 8ms 소요된 반면,
        두 번째 쿼리는 454건의 요청에 222ms가 소요되었습니다.
        요청 수가 적을수록 쿼리의 성능이 불안정해질 수 있습니다. 이는 데이터베이스 캐시와 관련이 있습니다.
        */

        /*
        범위 조건 검색 외에도 성능 하락을 초래할 수 있는 여러 종류의 조회가 있습니다.

        1. 조인(Join) 쿼리
        여러 테이블을 조인할 때, 특히 큰 테이블 간의 조인이 발생할 경우 성능이 저하될 수 있습니다.
        조인 키에 적절한 인덱스가 없으면 전체 테이블 스캔을 유발할 수 있습니다.

        2. 서브쿼리(Subquery)
        서브쿼리가 포함된 쿼리는 성능에 부정적인 영향을 미칠 수 있습니다.
        특히 서브쿼리에서 많은 데이터를 반환할 경우, 메인 쿼리의 성능이 크게 저하될 수 있습니다.

        3. 집계 함수(Aggregate Functions)
        COUNT, SUM, AVG 등의 집계 함수를 사용할 때,
        특히 그룹화(GROUP BY)와 함께 사용할 경우 성능이 낮아질 수 있습니다.
        집계 연산은 많은 데이터를 처리해야 하므로 시간이 걸립니다.

        4. LIKE 연산자
        LIKE 연산자를 사용할 때, 특히 %로 시작하는 패턴이 있는 경우,
        인덱스가 활용되지 않아 전체 테이블 스캔이 발생할 수 있습니다.

        5. ORDER BY와 GROUP BY
        ORDER BY와 GROUP BY 절을 사용할 때, 적절한 인덱스가 없다면 성능이 저하됩니다.
        이 경우에도 인덱스가 정렬에 도움이 되어야 성능을 개선할 수 있습니다.

        6. 대량의 데이터 업데이트/삭제
        대량의 데이터 업데이트나 삭제 작업이 이루어지면,
        인덱스가 재구성되거나 잠금이 발생할 수 있어 성능이 저하될 수 있습니다.

        7. NULL 값 처리
        NULL 값이 포함된 열에 대한 검색은 인덱스의 효율성을 떨어뜨릴 수 있습니다.
        NULL 값을 처리하는 방식에 따라 쿼리 성능이 달라질 수 있습니다.

        8. 복잡한 조건
        여러 조건이 조합된 경우(AND, OR 등), 특히 다양한 데이터 타입이 혼합되면 쿼리 성능이 저하될 수 있습니다.
        이와 같이 다양한 조건과 쿼리 구조가 성능에 영향을 미칠 수 있습니다.
        성능 저하를 방지하기 위해서는 적절한 인덱스 설계와 쿼리 최적화가 필요합니다.
         */
    }

    @Test
    void 회원이_가지고_있는_사용_가능한_쿠폰_조회() throws InterruptedException {
        AtomicBoolean running = new AtomicBoolean(false);
        AtomicInteger requestCount = new AtomicInteger(0);
        AtomicLong totalElapsedTime = new AtomicLong(0);

        int statusCode = RestAssured.get("/member-coupons/by-member-id?memberId=" + ThreadLocalRandom.current()
                .nextLong(MIN_MEMBER_ID, MAX_MEMBER_ID + 1)).statusCode();
        assertThat(statusCode).withFailMessage("회원이 가지고 있는 쿠폰 조회 API 호출에 실패했습니다. 테스트 대상 서버가 실행중인지 확인해 주세요.")
                .isEqualTo(200);

        executeMultipleRequests(running, requestCount, totalElapsedTime,
                () -> RestAssured.get("/member-coupons/by-member-id?memberId=" + ThreadLocalRandom.current()
                        .nextLong(MIN_MEMBER_ID, MAX_MEMBER_ID + 1)));

        System.out.println("Total request count: " + requestCount.get());
        System.out.println("Total elapsed time: " + totalElapsedTime.get() + "ms");

        long averageElapsedTime = totalElapsedTime.get() / requestCount.get();
        System.out.println("Average elapsed time: " + totalElapsedTime.get() / requestCount.get() + "ms");

        assertThat(averageElapsedTime).isLessThanOrEqualTo(100L);
        // before
        //Total request count: 519
        //Total elapsed time: 100757ms
        //Average elapsed time: 194ms

        // List<MemberCoupon> findByMemberIdAndUsedAndUseEndedAtAfter(Long memberId, boolean used, LocalDateTime now);

    }

    @Test
    void 월별_쿠폰_할인을_가장_많이_받은_회원_조회() throws InterruptedException {
        AtomicBoolean running = new AtomicBoolean(false);
        AtomicInteger requestCount = new AtomicInteger(0);
        AtomicLong totalElapsedTime = new AtomicLong(0);

        int statusCode = RestAssured.get("/marketing/max-coupon-discount-member?year=2019&month=1").statusCode();
        assertThat(statusCode).withFailMessage("월별 쿠폰 할인을 가장 많이 받은 회원 조회 API 호출에 실패했습니다. 테스트 대상 서버가 실행중인지 확인해 주세요.")
                .isEqualTo(200);

        executeMultipleRequests(running, requestCount, totalElapsedTime, () -> {
            RestAssured.get(
                    "/marketing/max-coupon-discount-member?year=2019&month=" + ThreadLocalRandom.current()
                            .nextInt(1, 6));
        });

        System.out.println("Total request count: " + requestCount.get());
        System.out.println("Total elapsed time: " + totalElapsedTime.get() + "ms");

        long averageElapsedTime = totalElapsedTime.get() / requestCount.get();
        System.out.println("Average elapsed time: " + totalElapsedTime.get() / requestCount.get() + "ms");

        assertThat(averageElapsedTime).isLessThanOrEqualTo(100L);
    }

    private void executeMultipleRequests(AtomicBoolean running,
                                         AtomicInteger requestCount,
                                         AtomicLong totalElapsedTime,
                                         Runnable runnable) throws InterruptedException {
        ExecutorService executorService = Executors.newFixedThreadPool(THREAD_COUNT);
        for (int i = 0; i < THREAD_COUNT; i++) {
            executorService.execute(() -> executeRequest(running, requestCount, totalElapsedTime, runnable));
        }

        Thread.sleep(MILLISECONDS_IN_SECOND);    // 스레드에 실행 요청 후 1초간 대기한 후 요청을 시작하도록 변경한다.
        running.set(true);
        Thread.sleep(TEST_DURATION_SECONDS * MILLISECONDS_IN_SECOND);
        running.set(false);

        executorService.shutdown();
        executorService.awaitTermination(10, TimeUnit.SECONDS);
    }

    private void executeRequest(AtomicBoolean running,
                                AtomicInteger requestCount,
                                AtomicLong totalElapsedTime,
                                Runnable runnable) {
        while (!running.get()) {
            // 요청을 시작할 때까지 대기한다.
        }

        long elapsedTime = 0;
        while (running.get()) {
            long startTime = System.currentTimeMillis();
            runnable.run();
            long endTime = System.currentTimeMillis();

            elapsedTime += endTime - startTime;
            requestCount.incrementAndGet();
        }

        totalElapsedTime.addAndGet(elapsedTime);
    }

}
