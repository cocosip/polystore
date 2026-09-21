package io.github.cocosip.polystore.fastdfs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.cocosip.polystore.exception.StorageOperationException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FastDfsProviderComponentsTest {

    @TempDir
    Path tempDir;

    @Test
    void indexShouldPersistMappingsAcrossReopens() {
        Path file = tempDir.resolve("index.properties");
        LocalFileIndex first = new LocalFileIndex(file);
        first.put("a.txt", "group1/M00/00/01/abc");
        first.put("b.txt", "group1/M00/00/01/def");
        first.remove("b.txt");

        LocalFileIndex reopened = new LocalFileIndex(file);
        assertThat(reopened.get("a.txt")).isEqualTo("group1/M00/00/01/abc");
        assertThat(reopened.get("b.txt")).isNull();
        assertThat(reopened.names()).containsExactly("a.txt");
    }

    @Test
    void trackerServerListShouldSplitOnCommasAndSemcolons() {
        assertThat(TobatoClientFactory.splitTrackerServers("10.0.0.1:22122, 10.0.0.2:22122;10.0.0.3:22122"))
                .containsExactly("10.0.0.1:22122", "10.0.0.2:22122", "10.0.0.3:22122");
        assertThat(TobatoClientFactory.splitTrackerServers(" , ; ")).isEmpty();
    }

    @Test
    void factoryShouldAssembleClientForWellFormedTrackers() {
        // initTracker validates address formats but does not dial the network
        com.github.tobato.fastdfs.service.FastFileStorageClient client =
                TobatoClientFactory.create(List.of("127.0.0.1:22122"), 1000, 3000, "UTF-8");

        assertThat(client).isNotNull();
    }

    @Test
    void factoryShouldRejectMalformedTrackers() {
        assertThatThrownBy(() -> TobatoClientFactory.create(List.of("not-a-host-port"), 1000, 3000, "UTF-8"))
                .isInstanceOf(StorageOperationException.class)
                .hasMessageContaining("tracker");
    }

    @Test
    void providerShouldRejectMissingTrackerServers() {
        assertThatThrownBy(() -> new FastDfsStorageProvider()
                        .createContainer(io.github.cocosip.polystore.ContainerConfiguration.builder()
                                .name("fast")
                                .type("fastdfs")
                                .build()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("trackerServers");
    }

    @Test
    void providerShouldBuildContainerWithDefaults() {
        var container = new FastDfsStorageProvider()
                .createContainer(io.github.cocosip.polystore.ContainerConfiguration.builder()
                        .name("fast")
                        .type("fastdfs")
                        .property("trackerServers", "127.0.0.1:22122")
                        .property("indexPath", tempDir.resolve("i.index").toString())
                        .build());

        assertThat(container.getProviderType()).isEqualTo("fastdfs");
        assertThat(container.getInfo().getName()).isEqualTo("fast");
        // unknown logical name behaves like a missing file
        assertThat(container.exists("nope.txt")).isFalse();
        assertThatThrownBy(() -> container.get("nope.txt"))
                .isInstanceOf(io.github.cocosip.polystore.exception.StorageFileNotFoundException.class);
    }
}
