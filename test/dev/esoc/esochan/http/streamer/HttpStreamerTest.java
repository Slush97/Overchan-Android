package dev.esoc.esochan.http.streamer;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class HttpStreamerTest {
    @Test
    public void automaticRetriesAreLimitedToGetRequests() {
        assertTrue(HttpStreamer.isAutomaticallyRetryable(HttpRequestModel.METHOD_GET));
        assertFalse(HttpStreamer.isAutomaticallyRetryable(HttpRequestModel.METHOD_POST));
        assertFalse(HttpStreamer.isAutomaticallyRetryable(HttpRequestModel.METHOD_DELETE));
    }
}
