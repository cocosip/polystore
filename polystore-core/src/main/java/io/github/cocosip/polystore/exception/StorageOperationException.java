package io.github.cocosip.polystore.exception;

/**
 * Thrown when a backend operation fails. I/O failures carry the backend-specific cause; local
 * validation failures (e.g. invalid file names) may be raised without one.
 */
public class StorageOperationException extends PolystoreException {

    private static final long serialVersionUID = 1L;

    /** Creates the exception with a message only, for failures without an underlying cause. */
    public StorageOperationException(String message) {
        super(message);
    }

    /** Creates the exception with a message and the backend cause. */
    public StorageOperationException(String message, Throwable cause) {
        super(message, cause);
    }
}
