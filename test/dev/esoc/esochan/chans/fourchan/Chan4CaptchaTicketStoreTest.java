package dev.esoc.esochan.chans.fourchan;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class Chan4CaptchaTicketStoreTest {

    private static final long HOUR = 60L * 60L * 1000L;

    @Test
    public void isExpiredFalseWithinTtl() {
        long storedAt = 1_000_000L;
        long now = storedAt + Chan4CaptchaTicketStore.TTL_MS;
        assertFalse(Chan4CaptchaTicketStore.isExpired(storedAt, now));
    }

    @Test
    public void isExpiredTrueAfterTtl() {
        long storedAt = 1_000_000L;
        long now = storedAt + Chan4CaptchaTicketStore.TTL_MS + 1;
        assertTrue(Chan4CaptchaTicketStore.isExpired(storedAt, now));
    }

    @Test
    public void isExpiredTrueForNonPositiveStoredAt() {
        assertTrue(Chan4CaptchaTicketStore.isExpired(0, System.currentTimeMillis()));
        assertTrue(Chan4CaptchaTicketStore.isExpired(-1, System.currentTimeMillis()));
    }

    @Test
    public void ttlIsSixHours() {
        assertTrue(Chan4CaptchaTicketStore.TTL_MS == 6 * HOUR);
    }

    @Test
    public void customTtlHonored() {
        assertFalse(Chan4CaptchaTicketStore.isExpired(10, 50, 100));
        assertTrue(Chan4CaptchaTicketStore.isExpired(10, 111, 100));
    }
}
