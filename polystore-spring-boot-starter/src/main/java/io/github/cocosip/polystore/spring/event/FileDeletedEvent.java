package io.github.cocosip.polystore.spring.event;

import org.springframework.context.ApplicationEvent;

/**
 * Published after a successful {@code delete} call on a container, only when the backend reported
 * that a file was actually removed ({@code delete} returning {@code true}).
 *
 * <p>Deleting a missing file reports {@code false} and publishes no event. The file name is the
 * logical name passed by the caller, before any tenant prefix is applied by the container.</p>
 */
public class FileDeletedEvent extends ApplicationEvent {

    private static final long serialVersionUID = 1L;

    /** Logical file name used by the caller, before any tenant prefix. */
    private final String fileName;

    /** Provider type identifier of the container, e.g. {@code minio}. */
    private final String providerType;

    /**
     * Creates the event for the given container and file.
     *
     * @param containerName name of the container the file was deleted from
     * @param fileName      logical file name used by the caller
     * @param providerType  provider type identifier of the container
     */
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
