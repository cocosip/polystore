package io.github.cocosip.polystore.spring;

import io.github.cocosip.polystore.ContainerInfo;
import io.github.cocosip.polystore.SaveArgs;
import io.github.cocosip.polystore.StorageContainer;
import io.github.cocosip.polystore.UrlArgs;
import io.github.cocosip.polystore.spring.event.FileDeletedEvent;
import io.github.cocosip.polystore.spring.event.FileSavedEvent;
import java.io.InputStream;
import java.util.Collection;

/**
 * Wraps a container and publishes {@link FileSavedEvent} / {@link FileDeletedEvent} after every
 * successful save / delete / deleteAll.
 *
 * <p>File names in events are the logical names passed by the caller, before any tenant prefix
 * is applied by an inner {@link TenantPathPrefixContainer}.</p>
 */
public final class EventPublishingContainer implements StorageContainer {

    private final StorageContainer inner;
    private final StorageEventPublisher publisher;

    /**
     * Creates the publishing view.
     *
     * @param inner     the container to wrap, never {@code null}
     * @param publisher event sink, never {@code null}
     */
    public EventPublishingContainer(StorageContainer inner, StorageEventPublisher publisher) {
        this.inner = inner;
        this.publisher = publisher;
    }

    @Override
    public void save(String fileName, InputStream inputStream, SaveArgs args) {
        inner.save(fileName, inputStream, args);
        publisher.publish(new FileSavedEvent(getName(), fileName, getProviderType()));
    }

    @Override
    public InputStream get(String fileName) {
        return inner.get(fileName);
    }

    @Override
    public void delete(String fileName) {
        inner.delete(fileName);
        publisher.publish(new FileDeletedEvent(getName(), fileName, getProviderType()));
    }

    @Override
    public boolean exists(String fileName) {
        return inner.exists(fileName);
    }

    @Override
    public String getUrl(String fileName, UrlArgs args) {
        return inner.getUrl(fileName, args);
    }

    @Override
    public void deleteAll(Collection<String> fileNames) {
        inner.deleteAll(fileNames);
        for (String fileName : fileNames) {
            publisher.publish(new FileDeletedEvent(getName(), fileName, getProviderType()));
        }
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
