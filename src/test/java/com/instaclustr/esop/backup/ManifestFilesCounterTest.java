package com.instaclustr.esop.backup;

import java.nio.file.Paths;

import com.instaclustr.esop.impl.Manifest.ManifestFilesCounter;
import com.instaclustr.esop.impl.ManifestEntry;
import org.testng.annotations.Test;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class ManifestFilesCounterTest {

    private static ManifestEntry entry(final String objectKey) {
        return new ManifestEntry(Paths.get(objectKey), null, ManifestEntry.Type.FILE, 100, null, null, null);
    }

    @Test
    public void testEntryInOneManifest() {
        final ManifestFilesCounter counter = new ManifestFilesCounter();
        counter.add("manifest-1", entry("data/ks/tb-1234/1-1/me-1-big-Data.db"));

        assertTrue(counter.isOnlyInOneManifest("data/ks/tb-1234/1-1/me-1-big-Data.db"));
        assertFalse(counter.isInMultipleManifests("data/ks/tb-1234/1-1/me-1-big-Data.db"));
    }

    @Test
    public void testEntryInMultipleManifests() {
        final ManifestFilesCounter counter = new ManifestFilesCounter();
        counter.add("manifest-1", entry("data/ks/tb-1234/1-1/me-1-big-Data.db"));
        counter.add("manifest-2", entry("data/ks/tb-1234/1-1/me-1-big-Data.db"));

        assertFalse(counter.isOnlyInOneManifest("data/ks/tb-1234/1-1/me-1-big-Data.db"));
        assertTrue(counter.isInMultipleManifests("data/ks/tb-1234/1-1/me-1-big-Data.db"));
    }

    @Test
    public void testMissingEntry() {
        final ManifestFilesCounter counter = new ManifestFilesCounter();
        counter.add("manifest-1", entry("data/ks/tb-1234/1-1/me-1-big-Data.db"));

        assertFalse(counter.isOnlyInOneManifest("data/ks/tb-1234/2-2/me-2-big-Data.db"));
        assertFalse(counter.isInMultipleManifests("data/ks/tb-1234/2-2/me-2-big-Data.db"));
    }
}
