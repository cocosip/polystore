package io.github.cocosip.polystore.spring.event;

import org.springframework.context.ApplicationEvent;

/**
 * Published after a successful {@code save} on a container.
 *
 * <p>The file name is the logical name passed by the caller, before any tenant prefix is applied
 * by the container.</p>
 */
public class FileSavedEvent extends ApplicationEvent {

    private static final long serialVersionUID = 1L;

    private final String fileName;
    private final String providerType;

    /** Creates the event for the given container and file. */
    public FileSavedEvent(String containerName, String fileName, String providerType) {
        super(containerName);
        this.fileName = fileName;
        this.providerType = providerType;
    }

    /**
     * Returns the name of the container the file was saved to.
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
