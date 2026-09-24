package com.instaclustr.esop.backup;

import java.nio.file.Paths;

import com.instaclustr.esop.impl.SSTableUtils;
import org.testng.annotations.Test;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class SSTableUtilsTest {

    @Test
    public void testMutableComponents() {
        assertTrue(SSTableUtils.isMutableComponent(Paths.get("/var/lib/cassandra/data/ks/tb-1234/me-661-big-Statistics.db")));
        assertTrue(SSTableUtils.isMutableComponent(Paths.get("me-661-big-Summary.db")));
        assertTrue(SSTableUtils.isMutableComponent(Paths.get("nb-12-big-Statistics.db")));
        assertTrue(SSTableUtils.isMutableComponent(Paths.get("ks-tb-ka-3-Statistics.db")));
    }

    @Test
    public void testImmutableComponents() {
        assertFalse(SSTableUtils.isMutableComponent(Paths.get("me-661-big-Data.db")));
        assertFalse(SSTableUtils.isMutableComponent(Paths.get("me-661-big-Index.db")));
        assertFalse(SSTableUtils.isMutableComponent(Paths.get("me-661-big-Filter.db")));
        assertFalse(SSTableUtils.isMutableComponent(Paths.get("me-661-big-CompressionInfo.db")));
        assertFalse(SSTableUtils.isMutableComponent(Paths.get("me-661-big-Digest.crc32")));
        assertFalse(SSTableUtils.isMutableComponent(Paths.get("me-661-big-TOC.txt")));
    }

    @Test
    public void testNonSSTableFiles() {
        assertFalse(SSTableUtils.isMutableComponent(Paths.get("Statistics.db")));
        assertFalse(SSTableUtils.isMutableComponent(Paths.get("schema.cql")));
        assertFalse(SSTableUtils.isMutableComponent(null));
    }
}
