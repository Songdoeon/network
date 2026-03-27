package com.network.gateway.transaction;

import com.network.common.dto.TransactionStatus;
import com.network.gateway.config.GatewayProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionLogTest {

    private TransactionLog transactionLog;

    @BeforeEach
    void setup() {
        GatewayProperties properties = new GatewayProperties();
        properties.getIdempotency().setTtlSeconds(300);
        transactionLog = new TransactionLog(properties);
    }

    @Test
    void record_andGet_returnsEntry() {
        transactionLog.record("tx-001", TransactionStatus.APPROVED, "OK", 50, "session-0", "merchant-1");

        TransactionLog.TransactionEntry entry = transactionLog.get("tx-001");
        assertThat(entry).isNotNull();
        assertThat(entry.txId()).isEqualTo("tx-001");
        assertThat(entry.status()).isEqualTo(TransactionStatus.APPROVED);
        assertThat(entry.reasonCode()).isEqualTo("OK");
        assertThat(entry.latencyMs()).isEqualTo(50);
        assertThat(entry.sessionId()).isEqualTo("session-0");
        assertThat(entry.merchantId()).isEqualTo("merchant-1");
        assertThat(entry.completedAt()).isNotNull();
    }

    @Test
    void get_unknownTxId_returnsNull() {
        assertThat(transactionLog.get("nonexistent")).isNull();
    }

    @Test
    void record_multipleEntries() {
        transactionLog.record("tx-001", TransactionStatus.APPROVED, "OK", 30, "s-0", "m-1");
        transactionLog.record("tx-002", TransactionStatus.TIMEOUT, "REQUEST_TIMEOUT", 2000, "s-1", "m-2");
        transactionLog.record("tx-003", TransactionStatus.ERROR, "UPSTREAM_DOWN", 100, "s-0", "m-3");

        assertThat(transactionLog.get("tx-001").status()).isEqualTo(TransactionStatus.APPROVED);
        assertThat(transactionLog.get("tx-002").status()).isEqualTo(TransactionStatus.TIMEOUT);
        assertThat(transactionLog.get("tx-003").status()).isEqualTo(TransactionStatus.ERROR);
    }

    @Test
    void record_overwritesSameTxId() {
        transactionLog.record("tx-001", TransactionStatus.APPROVED, "OK", 50, "s-0", "m-1");
        transactionLog.record("tx-001", TransactionStatus.DECLINED, "INSUFFICIENT_FUNDS", 60, "s-0", "m-1");

        TransactionLog.TransactionEntry entry = transactionLog.get("tx-001");
        assertThat(entry.status()).isEqualTo(TransactionStatus.DECLINED);
    }
}
