package io.github.cocosip.polystore.exception;

/**
 * Base class of all Polystore exceptions. Unchecked, like the operations they originate from.
 */
public class PolystoreException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** Creates the exception with a message. */
    public PolystoreException(String message) {
        super(message);
    }

    /** Creates the exception with a message and an underlying cause. */
    public PolystoreException(String message, Throwable cause) {
        super(message, cause);
    }
}
