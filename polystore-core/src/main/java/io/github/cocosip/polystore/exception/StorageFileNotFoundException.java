package io.github.cocosip.polystore.exception;

/**
 * Thrown when a file does not exist in the container.
 *
 * <p>Note the {@code Storage} prefix: the plain {@code FileNotFoundException} name is taken by
 * {@code java.io}, and the clash forces ugly qualified names in backend code.</p>
 */
public class StorageFileNotFoundException extends PolystoreException {

    private static final long serialVersionUID = 1L;

    private final String fileName;

    /**
     * Creates the exception for the given file name.
     *
     * @param fileName file name that could not be found
     */
    public StorageFileNotFoundException(String fileName) {
        super("File not found: " + fileName);
        this.fileName = fileName;
    }

    /**
     * Returns the file name that could not be found.
     *
     * @return file name
     */
    public String getFileName() {
        return fileName;
    }
}
