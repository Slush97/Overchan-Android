/*
 * esochan (Meta Imageboard Client)
 * Copyright (C) 2024-2026  esoc <https://github.com/esoc-dev>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.esoc.esochan.lib;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class UriFileUtilsTest {
    private static final String[] FOURCHAN_FORMATS = { "jpg", "jpeg", "png", "gif", "webm", "mp4" };

    @Test
    public void attachmentFilterMatchesExactExtensionIgnoringCase() {
        assertTrue(UriFileUtils.hasAllowedExtension("photo.JPEG", FOURCHAN_FORMATS));
        assertTrue(UriFileUtils.hasAllowedExtension("clip.webm", FOURCHAN_FORMATS));
        assertFalse(UriFileUtils.hasAllowedExtension("clip.webm.exe", FOURCHAN_FORMATS));
        assertFalse(UriFileUtils.hasAllowedExtension("not-really-jpg", FOURCHAN_FORMATS));
    }

    @Test
    public void attachmentFilterAllowsAnyFileWhenNoFilterExists() {
        assertTrue(UriFileUtils.hasAllowedExtension("archive.bin", null));
        assertTrue(UriFileUtils.hasAllowedExtension("archive.bin", new String[0]));
    }

    @Test
    public void sanitizeFileNameRemovesPathsControlCharactersAndLeadingDots() {
        assertEquals("_.._secret_image.jpg",
                UriFileUtils.sanitizeFileName("../..\\secret\nimage.jpg"));
        assertEquals("attachment", UriFileUtils.sanitizeFileName("..."));
        assertEquals("attachment", UriFileUtils.sanitizeFileName("  "));
    }

    @Test
    public void sanitizeFileNameLimitsLengthAndPreservesExtension() {
        String sanitized = UriFileUtils.sanitizeFileName("a".repeat(200) + ".webm");
        assertEquals(120, sanitized.length());
        assertTrue(sanitized.endsWith(".webm"));
    }
}
