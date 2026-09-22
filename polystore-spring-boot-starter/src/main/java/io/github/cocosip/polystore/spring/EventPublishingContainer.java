package io.github.cocosip.polystore.spring;

import io.github.cocosip.polystore.ContainerConfiguration;
import io.github.cocosip.polystore.ContainerInfo;
import io.github.cocosip.polystore.StorageContainer;
import io.github.cocosip.polystore.StorageSaveOptions;
import io.github.cocosip.polystore.spring.event.FileDeletedEvent;
import io.github.cocosip.polystore.spring.event.FileSavedEvent;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.Instant;

/** Public container decorator that publishes successful save and delete events. */
public final class EventPublishingContainer implements StorageContainer {
    private final StorageContainer inner;
    private final StorageEventPublisher publisher;

    /**
     * Creates the publishing view.
     *
     * @param inner     decorated container, never {@code null}
     * @param publisher event sink, never {@code null}
     */
    public EventPublishingContainer(StorageContainer inner, StorageEventPublisher publisher) {
        this.inner = inner;
        this.publisher = publisher;
    }

    @Override
    public String save(
            String fileId,
            InputStream stream,
            long contentLength,
            String ext,
            boolean overrideExisting,
            StorageSaveOptions options) {
        String savedFileId = inner.save(fileId, stream, contentLength, ext, overrideExisting, options);
        publisher.publish(new FileSavedEvent(getName(), fileId, getProviderType()));
        return savedFileId;
    }

    @Override
    public boolean delete(String fileId) {
        boolean deleted = inner.delete(fileId);
        if (deleted) publisher.publish(new FileDeletedEvent(getName(), fileId, getProviderType()));
        return deleted;
    }

    @Override
    public boolean exists(String fileId) {
        return inner.exists(fileId);
    }

    @Override
    public boolean download(String fileId, Path path) {
        return inner.download(fileId, path);
    }

    @Override
    public InputStream getOrNull(String fileId) {
        return inner.getOrNull(fileId);
    }

    @Override
    public String getAccessUrl(String fileId, Instant expires, boolean checkFileExist) {
        return inner.getAccessUrl(fileId, expires, checkFileExist);
    }

    @Override
    public ContainerConfiguration getConfiguration() {
        return inner.getConfiguration();
    }

    @Override
    public String getName() {
        return inner.getName();
    }

    @Override
    public String getProviderType() {
        return inner.getProviderType();
    }

    @Override
    public ContainerInfo getInfo() {
        return inner.getInfo();
    }
}
