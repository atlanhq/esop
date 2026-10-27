package com.instaclustr.esop.backup.manifestlisting;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.gax.paging.Page;
import com.google.cloud.ReadChannel;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.Storage.BlobListOption;
import com.instaclustr.esop.gcp.GCPModule.GoogleStorageFactory;
import com.instaclustr.esop.gcp.GCPRestorer;
import com.instaclustr.esop.impl.StorageLocation;
import com.instaclustr.esop.impl.list.ListOperationRequest;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.mockito.ArgumentMatchers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class GCPManifestListingTest {

    private static final String BUCKET = "bucket";
    private static final String NODE_1 = "cassandra/dc1/node-1/";
    private static final String NODE_2 = "cassandra/dc1/node-2/";
    private static final String NODE_3 = "cassandra/dc2/node-3/";

    private static final List<String> BUCKET_KEYS = Arrays.asList(
            NODE_1 + "manifests/snapshot-a.json",
            NODE_1 + "manifests/snapshot-b.json",
            NODE_1 + "data/ks/tb/manifests-lookalike.db",
            NODE_2 + "manifests/snapshot-c.json",
            NODE_3 + "manifests/snapshot-d.json",
            "artifacts/manifests/workflow-output.json",
            "other/cluster/dc/node/manifests/snapshot-e.json");

    private final List<String> listedPrefixes = new ArrayList<>();

    @BeforeMethod
    public void reset() {
        listedPrefixes.clear();
    }

    private static Blob blob(final String name, final boolean directory) {
        final Blob blob = mock(Blob.class);
        when(blob.getName()).thenReturn(name);
        when(blob.isDirectory()).thenReturn(directory);
        return blob;
    }

    @SuppressWarnings("unchecked")
    private Storage fakeStorage() throws Exception {
        final Storage storage = mock(Storage.class);

        when(storage.list(anyString(), ArgumentMatchers.<BlobListOption>any())).thenAnswer(invocation -> {
            String prefix = null;
            boolean currentDirectory = false;
            for (int i = 1; i < invocation.getArguments().length; i++) {
                final Object option = invocation.getArguments()[i];
                if (option instanceof BlobListOption) {
                    if (option.equals(BlobListOption.currentDirectory())) {
                        currentDirectory = true;
                    }
                    for (final String candidate : BUCKET_KEYS.stream().map(GCPManifestListingTest::allPrefixes).flatMap(List::stream).distinct().toArray(String[]::new)) {
                        if (option.equals(BlobListOption.prefix(candidate))) {
                            prefix = candidate;
                        }
                    }
                }
            }
            listedPrefixes.add(prefix);

            final List<Blob> result = new ArrayList<>();
            final Set<String> directories = new LinkedHashSet<>();
            for (final String key : BUCKET_KEYS) {
                if (prefix == null || !key.startsWith(prefix)) {
                    continue;
                }
                final int slash = key.indexOf('/', prefix.length());
                if (currentDirectory && slash >= 0) {
                    directories.add(key.substring(0, slash + 1));
                } else {
                    result.add(blob(key, false));
                }
            }
            directories.forEach(d -> result.add(blob(d, true)));

            final Page<Blob> page = mock(Page.class);
            when(page.iterateAll()).thenReturn(result);
            return page;
        });

        final ReadChannel channel = mock(ReadChannel.class);
        when(channel.read(any(ByteBuffer.class))).thenReturn(-1);
        when(storage.reader(any(BlobId.class))).thenReturn(channel);

        return storage;
    }

    private static List<String> allPrefixes(final String key) {
        final List<String> prefixes = new ArrayList<>();
        int slash = key.indexOf('/');
        while (slash >= 0) {
            prefixes.add(key.substring(0, slash + 1));
            slash = key.indexOf('/', slash + 1);
        }
        return prefixes;
    }

    private GCPRestorer restorer(final Storage storage, final String node) {
        final GoogleStorageFactory factory = mock(GoogleStorageFactory.class);
        when(factory.build()).thenReturn(storage);

        final ListOperationRequest request = new ListOperationRequest();
        request.storageLocation = new StorageLocation("gcp://" + BUCKET + "/cassandra/dc1/" + node);
        return new GCPRestorer(factory, request, new ObjectMapper());
    }

    private static List<String> downloadedKeys(final Path downloadDir) throws Exception {
        final List<String> keys = new ArrayList<>();
        try (java.util.stream.Stream<Path> files = Files.walk(downloadDir)) {
            files.filter(Files::isRegularFile).forEach(p -> keys.add(downloadDir.relativize(p).toString()));
        }
        keys.sort(String::compareTo);
        return keys;
    }

    @Test
    public void testOnlyManifestsUnderNodeDirectoriesOfTheClusterAreDownloaded() throws Exception {
        final Path downloadDir = Files.createTempDirectory("esop-gcp-manifests");
        restorer(fakeStorage(), "node-1").downloadManifestsToDirectory(downloadDir);

        assertEquals(downloadedKeys(downloadDir), Arrays.asList(NODE_1 + "manifests/snapshot-a.json",
                                                                NODE_1 + "manifests/snapshot-b.json",
                                                                NODE_2 + "manifests/snapshot-c.json",
                                                                NODE_3 + "manifests/snapshot-d.json"));
    }

    @Test
    public void testNoListingIsUnprefixedOrWalksBelowTheManifestsDirectory() throws Exception {
        final Path downloadDir = Files.createTempDirectory("esop-gcp-manifests");
        restorer(fakeStorage(), "node-1").downloadManifestsToDirectory(downloadDir);

        assertFalse(listedPrefixes.isEmpty());
        for (final String prefix : listedPrefixes) {
            assertTrue(prefix != null && !prefix.isEmpty(), "list call without a prefix");
            assertTrue(prefix.startsWith("cassandra/"), "list call outside the cluster: " + prefix);
        }
        assertEquals(listedPrefixes, Arrays.asList("cassandra/",
                                                   "cassandra/dc1/",
                                                   NODE_1 + "manifests/",
                                                   NODE_2 + "manifests/",
                                                   "cassandra/dc2/",
                                                   NODE_3 + "manifests/"));
    }

    @Test
    public void testDownloadDirectoryIsCleanedBeforeDownload() throws Exception {
        final Path downloadDir = Files.createTempDirectory("esop-gcp-manifests");
        final Path stale = downloadDir.resolve(Paths.get("stale", "manifests", "old.json"));
        Files.createDirectories(stale.getParent());
        Files.write(stale, new byte[0]);

        restorer(fakeStorage(), "node-1").downloadManifestsToDirectory(downloadDir);

        assertFalse(Files.exists(stale));
    }
}
