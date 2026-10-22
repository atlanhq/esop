package com.instaclustr.esop.backup.manifestlisting;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.instaclustr.esop.azure.AzureModule.CloudStorageAccountFactory;
import com.instaclustr.esop.azure.AzureRestorer;
import com.instaclustr.esop.impl.StorageLocation;
import com.instaclustr.esop.impl.list.ListOperationRequest;
import com.microsoft.azure.storage.CloudStorageAccount;
import com.microsoft.azure.storage.blob.BlobListingDetails;
import com.microsoft.azure.storage.blob.CloudBlobClient;
import com.microsoft.azure.storage.blob.CloudBlobContainer;
import com.microsoft.azure.storage.blob.CloudBlobDirectory;
import com.microsoft.azure.storage.blob.CloudBlockBlob;
import com.microsoft.azure.storage.blob.ListBlobItem;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class AzureManifestListingTest {

    private static final String CONTAINER = "container";
    private static final String NODE_1 = "cassandra/dc1/node-1/";
    private static final String NODE_2 = "cassandra/dc1/node-2/";
    private static final String NODE_3 = "cassandra/dc2/node-3/";

    private static final List<String> CONTAINER_KEYS = Arrays.asList(
            NODE_1 + "manifests/snapshot-a.json",
            NODE_1 + "manifests/snapshot-b.json",
            NODE_1 + "data/ks/tb/manifests-lookalike.db",
            NODE_2 + "manifests/snapshot-c.json",
            NODE_3 + "manifests/snapshot-d.json",
            "artifacts/manifests/workflow-output.json",
            "other/cluster/dc/node/manifests/snapshot-e.json");

    private final List<String> listedPrefixes = new ArrayList<>();
    private final List<Boolean> flatListings = new ArrayList<>();

    @BeforeMethod
    public void reset() {
        listedPrefixes.clear();
        flatListings.clear();
    }

    private AzureRestorer restorer() throws Exception {
        final CloudBlobContainer container = mock(CloudBlobContainer.class);
        when(container.getName()).thenReturn(CONTAINER);

        doAnswer(invocation -> {
            final String prefix = (String) invocation.getArguments()[0];
            final boolean flat = (Boolean) invocation.getArguments()[1];
            listedPrefixes.add(prefix);
            flatListings.add(flat);

            final List<ListBlobItem> result = new ArrayList<>();
            final Set<String> directories = new LinkedHashSet<>();
            for (final String key : CONTAINER_KEYS) {
                if (!key.startsWith(prefix)) {
                    continue;
                }
                final int slash = key.indexOf('/', prefix.length());
                if (!flat && slash >= 0) {
                    directories.add(key.substring(0, slash + 1));
                } else {
                    final CloudBlockBlob blob = mock(CloudBlockBlob.class);
                    when(blob.getUri()).thenReturn(new URI("https://account.blob.core.windows.net/" + CONTAINER + "/" + key));
                    result.add(blob);
                }
            }
            for (final String directory : directories) {
                final CloudBlobDirectory blobDirectory = mock(CloudBlobDirectory.class);
                when(blobDirectory.getPrefix()).thenReturn(directory);
                result.add(blobDirectory);
            }
            return result;
        }).when(container).listBlobs(anyString(), anyBoolean(), any(EnumSet.class), any(), any());

        when(container.getBlockBlobReference(anyString())).thenReturn(mock(CloudBlockBlob.class));

        final CloudBlobClient client = mock(CloudBlobClient.class);
        when(client.getContainerReference(eq(CONTAINER))).thenReturn(container);
        final CloudStorageAccount account = mock(CloudStorageAccount.class);
        when(account.createCloudBlobClient()).thenReturn(client);
        final CloudStorageAccountFactory factory = mock(CloudStorageAccountFactory.class);
        when(factory.build(any())).thenReturn(account);

        final ListOperationRequest request = new ListOperationRequest();
        request.storageLocation = new StorageLocation("azure://" + CONTAINER + "/cassandra/dc1/node-1");
        return new AzureRestorer(factory, request, new ObjectMapper());
    }

    @Test
    public void testNoListingIsUnprefixedOrWalksBelowTheManifestsDirectory() throws Exception {
        final Path downloadDir = Files.createTempDirectory("esop-azure-manifests");
        restorer().downloadManifestsToDirectory(downloadDir);

        assertFalse(listedPrefixes.isEmpty());
        for (final String prefix : listedPrefixes) {
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
    public void testDirectoryLevelsAreListedNonFlat() throws Exception {
        restorer().downloadManifestsToDirectory(Files.createTempDirectory("esop-azure-manifests"));

        assertFalse(flatListings.get(0), "cluster level must not be listed flat");
        assertFalse(flatListings.get(1), "dc level must not be listed flat");
    }

    @Test
    public void testDownloadDirectoryIsCleanedBeforeDownload() throws Exception {
        final Path downloadDir = Files.createTempDirectory("esop-azure-manifests");
        final Path stale = downloadDir.resolve(Paths.get("stale", "manifests", "old.json"));
        Files.createDirectories(stale.getParent());
        Files.write(stale, new byte[0]);

        restorer().downloadManifestsToDirectory(downloadDir);

        assertFalse(Files.exists(stale));
    }
}
