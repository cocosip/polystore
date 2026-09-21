package io.github.cocosip.polystore.exception;

/**
 * Thrown when the provider type referenced by a container configuration is not registered.
 */
public class StorageProviderNotFoundException extends PolystoreException {

    private static final long serialVersionUID = 1L;

    private final String providerType;

    /** Creates the exception for the given provider type identifier. */
    public StorageProviderNotFoundException(String providerType) {
        super("Storage provider type not registered: " + providerType);
        this.providerType = providerType;
    }

    /**
     * Returns the provider type identifier that could not be resolved.
     *
     * @return provider type identifier
     */
    public String getProviderType() {
        return providerType;
    }
}
