package io.github.cocosip.polystore.exception;

/**
 * Thrown when a file already exists and the save operation does not allow replacement.
 *
 * <p>Note the {@code Storage} prefix: the plain {@code FileAlreadyExistsException} name is taken by
 * {@code java.nio.file}, and the clash forces ugly qualified names in backend code.</p>
 */
public class StorageFileAlreadyExistsException extends PolystoreException {

    private static final long serialVersionUID = 1L;

    private final String fileName;

    /**
     * Creates the exception for the given file name.
     *
     * @param fileName file name that already exists
     */
    public StorageFileAlreadyExistsException(String fileName) {
        super("File already exists: " + fileName);
        this.fileName = fileName;
    }

    /**
     * Returns the file name that already exists.
     *
     * @return file name
     */
    public String getFileName() {
        return fileName;
    }
}
