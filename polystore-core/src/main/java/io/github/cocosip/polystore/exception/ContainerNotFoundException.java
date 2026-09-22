package io.github.cocosip.polystore.exception;

/**
 * Thrown when a container name is not registered in the {@code StorageManager}.
 */
public class ContainerNotFoundException extends PolystoreException {

    private static final long serialVersionUID = 1L;

    private final String containerName;

    /**
     * Creates the exception for the given container name.
     *
     * @param containerName container name that could not be found
     */
    public ContainerNotFoundException(String containerName) {
        super("Storage container not found: " + containerName);
        this.containerName = containerName;
    }

    /**
     * Returns the container name that could not be found.
     *
     * @return container name
     */
    public String getContainerName() {
        return containerName;
    }
}
