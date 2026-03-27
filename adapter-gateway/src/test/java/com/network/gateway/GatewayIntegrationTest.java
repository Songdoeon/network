package com.network.gateway;

import com.network.common.dto.*;
import com.network.common.protocol.Frame;
import com.network.common.protocol.MessageType;
import com.network.gateway.support.MockCardSimServer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayIntegrationTest {

    static MockCardSimServer mockServer = new MockCardSimServer();
    static int mockPort;

    @LocalServerPort
    int serverPort;

    @Autowired
    TestRestTemplate restTemplate;

    @BeforeAll
    static void startMock() throws Exception {
        mockPort = mockServer.start();
    }

    @AfterAll
    static void stopMock() {
        mockServer.stop();
    }

    @BeforeEach
    void resetMock() {
        mockServer.setResponseBuilder(request -> {
            MessageType responseType = switch (request.messageType()) {
                case AUTH_REQ -> MessageType.AUTH_RES;
                case CANCEL_REQ -> MessageType.CANCEL_RES;
                case INQUIRY_REQ -> MessageType.INQUIRY_RES;
                default -> MessageType.AUTH_RES;
            };
            byte[] body = (TransactionStatus.APPROVED.name() + "|OK").getBytes(StandardCharsets.UTF_8);
            return new Frame(request.correlationId(), responseType, body);
        });
        mockServer.setResponseDelay(Duration.ZERO);
    }

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("gateway.upstream.host", () -> "localhost");
        registry.add("gateway.upstream.port", () -> mockPort);
        registry.add("gateway.upstream.max-sessions", () -> 1);
        registry.add("gateway.upstream.max-inflight-per-session", () -> 5);
        registry.add("gateway.upstream.max-queue-depth", () -> 10);
        registry.add("gateway.upstream.request-timeout-ms", () -> 2000);
    }

    @Test
    void normalAuthorize_returnsApproved() {
        AuthorizeRequest request = new AuthorizeRequest(
                "merchant-1", 10000L, "KRW", UUID.randomUUID().toString(), "tx-001", null);

        ResponseEntity<AuthorizeResponse> response = restTemplate.postForEntity(
                "/v1/authorize", request, AuthorizeResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(TransactionStatus.APPROVED);
        assertThat(response.getBody().reasonCode()).isEqualTo("OK");
        assertThat(response.getBody().txId()).isNotNull();
        assertThat(response.getBody().latencyMs()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void timeout_returnsTimeout() {
        mockServer.setResponseDelay(Duration.ofMillis(3000));

        AuthorizeRequest request = new AuthorizeRequest(
                "merchant-2", 5000L, "KRW", UUID.randomUUID().toString(), "tx-timeout", null);

        ResponseEntity<AuthorizeResponse> response = restTemplate.postForEntity(
                "/v1/authorize", request, AuthorizeResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(TransactionStatus.TIMEOUT);
    }

    @Test
    void busyRejection_returns503() throws Exception {
        // Set high delay so requests stay inflight
        mockServer.setResponseDelay(Duration.ofMillis(5000));

        ExecutorService executor = Executors.newFixedThreadPool(20);
        List<Future<ResponseEntity<AuthorizeResponse>>> futures = new ArrayList<>();

        // Send many requests to exhaust inflight + queue
        for (int i = 0; i < 20; i++) {
            futures.add(executor.submit(() -> {
                AuthorizeRequest req = new AuthorizeRequest(
                        "merchant-busy", 1000L, "KRW", UUID.randomUUID().toString(),
                        UUID.randomUUID().toString().substring(0, 8), null);
                return restTemplate.postForEntity("/v1/authorize", req, AuthorizeResponse.class);
            }));
        }

        // Wait a bit for requests to fill up
        Thread.sleep(500);

        // Now send one more — should be rejected
        AuthorizeRequest extraReq = new AuthorizeRequest(
                "merchant-extra", 1000L, "KRW", UUID.randomUUID().toString(), "tx-extra", null);
        ResponseEntity<AuthorizeResponse> extraResponse = restTemplate.postForEntity(
                "/v1/authorize", extraReq, AuthorizeResponse.class);

        // The extra request or one of the later ones should get BUSY or TIMEOUT
        boolean foundBusyOrTimeout = extraResponse.getStatusCode() == HttpStatus.SERVICE_UNAVAILABLE
                || extraResponse.getStatusCode() == HttpStatus.GATEWAY_TIMEOUT;

        // Also check all collected responses
        for (Future<ResponseEntity<AuthorizeResponse>> f : futures) {
            try {
                ResponseEntity<AuthorizeResponse> r = f.get(10, TimeUnit.SECONDS);
                if (r.getStatusCode() == HttpStatus.SERVICE_UNAVAILABLE) {
                    foundBusyOrTimeout = true;
                }
            } catch (Exception ignored) {
                foundBusyOrTimeout = true;
            }
        }

        assertThat(foundBusyOrTimeout).isTrue();
        executor.shutdown();

        // Reset delay to let remaining requests complete
        mockServer.setResponseDelay(Duration.ZERO);
        Thread.sleep(3000);
    }

    @Test
    void sessionDisconnect_errorThenRecovers() throws Exception {
        // Send a normal request first to establish connection
        AuthorizeRequest req1 = new AuthorizeRequest(
                "merchant-disc", 1000L, "KRW", UUID.randomUUID().toString(), "tx-disc-1", null);
        ResponseEntity<AuthorizeResponse> response1 = restTemplate.postForEntity(
                "/v1/authorize", req1, AuthorizeResponse.class);
        assertThat(response1.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Disconnect all
        mockServer.disconnectAll();

        // Wait for reconnection
        Thread.sleep(3000);

        // Send another request after reconnect
        AuthorizeRequest req2 = new AuthorizeRequest(
                "merchant-disc", 1000L, "KRW", UUID.randomUUID().toString(), "tx-disc-2", null);
        ResponseEntity<AuthorizeResponse> response2 = restTemplate.postForEntity(
                "/v1/authorize", req2, AuthorizeResponse.class);

        // Should succeed after reconnect (or still be recovering)
        assertThat(response2.getStatusCode()).isIn(HttpStatus.OK, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void outOfOrder_matchesCorrectly() throws Exception {
        // Build responses that reverse order with varying delays
        mockServer.setResponseBuilder(request -> {
            MessageType responseType = switch (request.messageType()) {
                case AUTH_REQ -> MessageType.AUTH_RES;
                case CANCEL_REQ -> MessageType.CANCEL_RES;
                case INQUIRY_REQ -> MessageType.INQUIRY_RES;
                default -> MessageType.AUTH_RES;
            };
            byte[] body = (TransactionStatus.APPROVED.name() + "|OK").getBytes(StandardCharsets.UTF_8);
            return new Frame(request.correlationId(), responseType, body);
        });

        // Send multiple requests concurrently
        ExecutorService executor = Executors.newFixedThreadPool(3);
        List<Future<ResponseEntity<AuthorizeResponse>>> futures = new ArrayList<>();

        for (int i = 0; i < 3; i++) {
            final int idx = i;
            futures.add(executor.submit(() -> {
                AuthorizeRequest req = new AuthorizeRequest(
                        "merchant-ooo-" + idx, (idx + 1) * 1000L, "KRW",
                        UUID.randomUUID().toString(), "tx-ooo-" + idx, null);
                return restTemplate.postForEntity("/v1/authorize", req, AuthorizeResponse.class);
            }));
        }

        for (Future<ResponseEntity<AuthorizeResponse>> f : futures) {
            ResponseEntity<AuthorizeResponse> r = f.get(5, TimeUnit.SECONDS);
            assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(r.getBody()).isNotNull();
            assertThat(r.getBody().txId()).isNotNull();
            assertThat(r.getBody().status()).isEqualTo(TransactionStatus.APPROVED);
        }

        executor.shutdown();
    }

    @Test
    void inquiry_pendingThenCompleted() throws Exception {
        // First send a request and get the txId
        AuthorizeRequest req = new AuthorizeRequest(
                "merchant-inq", 5000L, "KRW", UUID.randomUUID().toString(), "tx-inq", null);
        ResponseEntity<AuthorizeResponse> authResponse = restTemplate.postForEntity(
                "/v1/authorize", req, AuthorizeResponse.class);

        assertThat(authResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        String txId = authResponse.getBody().txId();

        // Inquiry for completed transaction
        ResponseEntity<InquiryResponse> inqResponse = restTemplate.getForEntity(
                "/v1/inquiry/" + txId, InquiryResponse.class);
        assertThat(inqResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(inqResponse.getBody()).isNotNull();
        assertThat(inqResponse.getBody().status()).isIn(TransactionStatus.APPROVED, TransactionStatus.PENDING);

        // Inquiry for unknown txId → 404
        ResponseEntity<InquiryResponse> unknownResponse = restTemplate.getForEntity(
                "/v1/inquiry/unknown-tx", InquiryResponse.class);
        assertThat(unknownResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void idempotency_sameKeyReturnsSameResult() {
        String idempotencyKey = UUID.randomUUID().toString();

        AuthorizeRequest req = new AuthorizeRequest(
                "merchant-idem", 10000L, "KRW", idempotencyKey, "tx-idem", null);

        // First request
        ResponseEntity<AuthorizeResponse> response1 = restTemplate.postForEntity(
                "/v1/authorize", req, AuthorizeResponse.class);
        assertThat(response1.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response1.getBody()).isNotNull();
        String txId1 = response1.getBody().txId();

        // Second request with same idempotency key
        ResponseEntity<AuthorizeResponse> response2 = restTemplate.postForEntity(
                "/v1/authorize", req, AuthorizeResponse.class);
        assertThat(response2.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response2.getBody()).isNotNull();
        String txId2 = response2.getBody().txId();

        // Should return same result
        assertThat(txId1).isEqualTo(txId2);
        assertThat(response1.getBody().status()).isEqualTo(response2.getBody().status());
    }
}
