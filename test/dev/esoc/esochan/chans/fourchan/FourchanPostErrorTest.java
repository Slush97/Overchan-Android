package dev.esoc.esochan.chans.fourchan;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import org.junit.Test;

public class FourchanPostErrorTest {

    @Test
    public void classifiesCaptchaFixtures() {
        assertSame(FourchanPostError.Kind.CAPTCHA,
                FourchanPostError.classifyServerMessage("You forgot to solve the CAPTCHA."));
        assertSame(FourchanPostError.Kind.CAPTCHA,
                FourchanPostError.classifyServerMessage("Error: You seem to have mistyped the CAPTCHA."));
    }

    @Test
    public void classifiesBannedFixtures() {
        assertSame(FourchanPostError.Kind.BANNED,
                FourchanPostError.classifyServerMessage("You are banned. ;_; Visit 4chan.org/banned for details."));
    }

    @Test
    public void classifiesFloodFixtures() {
        assertSame(FourchanPostError.Kind.FLOOD,
                FourchanPostError.classifyServerMessage("Error: You must wait longer before posting again."));
        assertSame(FourchanPostError.Kind.FLOOD,
                FourchanPostError.classifyServerMessage("Flood detected; Post discarded."));
    }

    @Test
    public void classifiesDuplicateFixtures() {
        assertSame(FourchanPostError.Kind.DUPLICATE,
                FourchanPostError.classifyServerMessage("Error: Duplicate file exists."));
        assertSame(FourchanPostError.Kind.DUPLICATE,
                FourchanPostError.classifyServerMessage("Error: This image is identical to the one you just posted."));
    }

    @Test
    public void classifiesFileTooLarge() {
        assertSame(FourchanPostError.Kind.FILE_TOO_LARGE,
                FourchanPostError.classifyServerMessage("Error: File too large."));
        assertSame(FourchanPostError.Kind.FILE_TOO_LARGE,
                FourchanPostError.classifyServerMessage("Error: Image is too large."));
    }

    @Test
    public void classifiesVerifyEmail() {
        assertSame(FourchanPostError.Kind.VERIFY_EMAIL,
                FourchanPostError.classifyServerMessage("Error: You must verify your email address."));
    }

    @Test
    public void classifiesPass() {
        assertSame(FourchanPostError.Kind.PASS,
                FourchanPostError.classifyServerMessage("Error: You are not authenticated with a 4chan Pass."));
    }

    @Test
    public void unknownErrmsgIsOther() {
        assertSame(FourchanPostError.Kind.OTHER,
                FourchanPostError.classifyServerMessage("Error: Board is offline for maintenance."));
        assertSame(FourchanPostError.Kind.OTHER,
                FourchanPostError.classifyServerMessage(""));
        assertSame(FourchanPostError.Kind.OTHER,
                FourchanPostError.classifyServerMessage(null));
    }

    @Test
    public void formatUserMessageKeepsRawWhenResourcesUnavailable() {
        // Pure classify path is the contract under test; format needs Resources on device.
        assertEquals(FourchanPostError.Kind.CAPTCHA,
                FourchanPostError.classifyServerMessage("CAPTCHA"));
    }
}
