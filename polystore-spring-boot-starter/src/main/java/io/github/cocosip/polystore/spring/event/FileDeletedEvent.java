package io.github.cocosip.polystore.spring.event;

import org.springframework.context.ApplicationEvent;

/**
 * Published after a successful {@code delete} or {@code deleteAll} call on a container, one event
 * per file name.
 *
 * <p>Backends treat deleting a missing file as a no-op, so consumers must not assume the file
 * actually existed. The file name is the logical name passed by the caller, before any tenant
 * prefix is applied by the container.</p>
 */
public class FileDeletedEvent extends ApplicationEvent {

    private static final long serialVersionUID = 1L;

    private final String fileName;
    private final String providerType;

    /** Creates the event for the given container and file. */
    public FileDeletedEvent(String containerName, String fileName, String providerType) {
        super(containerName);
        this.fileName = fileName;
        this.providerType = providerType;
    }

    /**
     * Returns the name of the container the file was deleted from.
     *
     * @return container name
     */
    public String getContainerName() {
        return (String) getSource();
    }

    /**
     * Returns the logical file name used by the caller.
     *
     * @return file name
     */
    public String getFileName() {
        return fileName;
    }

    /**
     * Returns the provider type identifier of the container, e.g. {@code minio}.
     *
     * @return provider type identifier
     */
    public String getProviderType() {
        return providerType;
    }
}
