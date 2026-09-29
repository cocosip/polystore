package io.github.cocosip.polystore.local;

import io.github.cocosip.polystore.StorageBackend;
import io.github.cocosip.polystore.StorageProviderAccessArgs;
import io.github.cocosip.polystore.StorageProviderDeleteArgs;
import io.github.cocosip.polystore.StorageProviderDownloadArgs;
import io.github.cocosip.polystore.StorageProviderExistsArgs;
import io.github.cocosip.polystore.StorageProviderGetArgs;
import io.github.cocosip.polystore.StorageProviderSaveArgs;
import io.github.cocosip.polystore.exception.StorageFileAlreadyExistsException;
import io.github.cocosip.polystore.exception.StorageOperationException;
import io.github.cocosip.polystore.util.ExactLengthInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;

/** Filesystem {@link StorageBackend}. */
public final class LocalStorageClient implements StorageBackend {
    private final Path basePath;
    private final Path root;
    private final String containerName;
    private final boolean appendContainerNameToBasePath;
    private final String httpServer;
    private final boolean createDirectories;

    /**
     * Creates the filesystem backend.
     *
     * @param basePath                     storage root directory, never {@code null}
     * @param containerName                owning container name, may be empty
     * @param appendContainerNameToBasePath store files under {@code basePath}/{@code containerName}
     * @param httpServer                   URL prefix for access URLs, may be empty
     * @param createDirectories            create the missing base directory during construction
     */
    public LocalStorageClient(
            Path basePath,
            String containerName,
            boolean appendContainerNameToBasePath,
            String httpServer,
            boolean createDirectories) {
        this.basePath = Objects.requireNonNull(basePath, "basePath");
        this.containerName = containerName == null ? "" : containerName.trim();
        this.appendContainerNameToBasePath = appendContainerNameToBasePath;
        this.httpServer = httpServer == null ? "" : httpServer.trim();
        this.createDirectories = createDirectories;
        this.root = this.appendContainerNameToBasePath && !this.containerName.isEmpty()
                ? basePath.resolve(this.containerName).normalize()
                : basePath.normalize();
        if (createDirectories) {
            try {
                Files.createDirectories(basePath);
            } catch (Exception e) {
                throw new StorageOperationException("Cannot create base directory " + basePath, e);
            }
        }
    }

    @Override
    public String save(StorageProviderSaveArgs args) {
        Path target = resolve(args.getFileId());
        if (!args.isOverrideExisting() && Files.exists(target)) {
            throw new StorageFileAlreadyExistsException(args.getFileId());
        }
        ExactLengthInputStream bounded = new ExactLengthInputStream(args.getFileStream(), args.getContentLength());
        try {
            Path parent = target.getParent();
            if (parent != null && createDirectories) Files.createDirectories(parent);
            // write to a temporary file first, so a failed save never leaves a truncated target
            Path temp = parent != null
                    ? Files.createTempFile(parent, "polystore-", ".tmp")
                    : Files.createTempFile("polystore-", ".tmp");
            try {
                Files.copy(bounded, temp, StandardCopyOption.REPLACE_EXISTING);
                bounded.verifyComplete();
                moveIntoPlace(temp, target);
            } catch (Exception primary) {
                try {
                    Files.deleteIfExists(temp);
                } catch (Exception cleanup) {
                    primary.addSuppressed(cleanup);
                }
                throw primary;
            }
            return args.getFileId();
        } catch (Exception e) {
            throw new StorageOperationException("Failed to save file: " + args.getFileId(), e);
        }
    }

    private static void moveIntoPlace(Path temp, Path target) throws IOException {
        try {
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @Override
    public InputStream getOrNull(StorageProviderGetArgs args) {
        Path target = resolve(args.getFileId());
        if (!Files.exists(target)) return null;
        try {
            return Files.newInputStream(target);
        } catch (NoSuchFileException e) {
            // the file vanished between the existence check and the open; report it as missing
            return null;
        } catch (Exception e) {
            throw new StorageOperationException("Failed to get file: " + args.getFileId(), e);
        }
    }

    @Override
    public boolean delete(StorageProviderDeleteArgs args) {
        Path target = resolve(args.getFileId());
        try {
            return Files.deleteIfExists(target);
        } catch (Exception e) {
            throw new StorageOperationException("Failed to delete file: " + args.getFileId(), e);
        }
    }

    @Override
    public boolean exists(StorageProviderExistsArgs args) {
        return Files.exists(resolve(args.getFileId()));
    }

    @Override
    public boolean download(StorageProviderDownloadArgs args) {
        Path source = resolve(args.getFileId());
        if (!Files.exists(source)) return false;
        try {
            Files.copy(source, args.getPath(), StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (Exception e) {
            throw new StorageOperationException("Failed to download file: " + args.getFileId(), e);
        }
    }

    @Override
    public String getAccessUrl(StorageProviderAccessArgs args) {
        String relativePath = relativePath(args.getFileId());
        String encodedPath = encodePath(relativePath);
        return httpServer.isEmpty() ? encodedPath : ensureTrailingSlash(httpServer) + trimLeadingSlashes(encodedPath);
    }

    /**
     * Percent-encodes every path segment so file ids containing spaces, {@code #} or {@code ?}
     * produce usable URLs. Segments already made of unreserved characters stay unchanged.
     */
    private static String encodePath(String path) {
        StringBuilder encoded = new StringBuilder(path.length());
        for (String segment : path.split("/", -1)) {
            if (!encoded.isEmpty()) {
                encoded.append('/');
            }
            encoded.append(URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20"));
        }
        return encoded.toString();
    }

    private Path resolve(String fileId) {
        try {
            Path resolved = basePath.resolve(relativePath(fileId)).normalize();
            // self-referential ids like "." or "a/.." normalize onto the root directory itself,
            // where save would move a file onto a directory and delete would remove it
            if (!resolved.startsWith(root) || resolved.equals(root)) {
                throw new StorageOperationException("File id escapes the base path: " + fileId);
            }
            return resolved;
        } catch (StorageOperationException e) {
            throw e;
        } catch (Exception e) {
            throw new StorageOperationException("Invalid file id: " + fileId, e);
        }
    }

    private String relativePath(String fileId) {
        return appendContainerNameToBasePath && !containerName.isEmpty() ? containerName + "/" + fileId : fileId;
    }

    private static String ensureTrailingSlash(String value) {
        return value.endsWith("/") ? value : value + "/";
    }

    private static String trimLeadingSlashes(String value) {
        int index = 0;
        while (index < value.length() && value.charAt(index) == '/') index++;
        return value.substring(index);
    }
}
