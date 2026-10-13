package com.instaclustr.esop.backup;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import com.instaclustr.esop.impl.Manifest.ManifestAgeComparator;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

public class ManifestAgeComparatorTest {

    private static final long DAY_MS = 24L * 60 * 60 * 1000;

    private static String manifest(final long timestamp) {
        return "snapshot-1234-" + timestamp + ".json";
    }

    @Test
    public void testNewestFirstWhenManifestsAreMoreThanOverflowApart() {
        final long newest = 1_760_000_000_000L;
        final List<String> expected = new ArrayList<>();
        for (int i = 0; i < 600; i++) {
            expected.add(manifest(newest - i * DAY_MS / 20));
        }

        final Random random = new Random(42);
        for (int run = 0; run < 200; run++) {
            final List<String> shuffled = new ArrayList<>(expected);
            Collections.shuffle(shuffled, random);
            shuffled.sort(new ManifestAgeComparator());
            assertEquals(shuffled, expected);
        }
    }

    @Test
    public void testSignForManifestsThirtyDaysApart() {
        final ManifestAgeComparator comparator = new ManifestAgeComparator();
        final String older = manifest(1_760_000_000_000L - 30 * DAY_MS);
        final String newer = manifest(1_760_000_000_000L);

        assertEquals(Integer.signum(comparator.compare(older, newer)), 1);
        assertEquals(Integer.signum(comparator.compare(newer, older)), -1);
        assertEquals(comparator.compare(newer, newer), 0);
    }
}
