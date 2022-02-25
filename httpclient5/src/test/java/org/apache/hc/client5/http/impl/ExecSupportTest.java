package org.apache.hc.client5.http.impl;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ExecSupportTest {

    @Test
    public void testGetNextExchangeId() {
        long base = ExecSupport.getNextExecNumber();
        for (int i = 1; i <= 1_000_000; i++) {
            Assertions.assertEquals(
                String.format("ex-%010d", i + base),
                ExecSupport.getNextExchangeId());
        }
    }

    @Test
    public void testCreateId() {
        long base = 9_999_999_000L;
        for (int i = 0; i <= 1_000_000; i++) {
            Assertions.assertEquals(
                String.format("ex-%010d", i + base),
                ExecSupport.createId(base + i));
        }
    }
}
