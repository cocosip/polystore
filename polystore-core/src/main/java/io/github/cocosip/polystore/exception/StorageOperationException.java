package io.github.cocosip.polystore.exception;

/**
 * Thrown when a backend I/O operation fails. Always carries the backend-specific cause.
 */
public class StorageOperationException extends PolystoreException {

    private static final long serialVersionUID = 1L;

    /** Creates the exception with a message and the backend cause. */
    public StorageOperationException(String message, Throwable cause) {
        super(message, cause);
    }
}
