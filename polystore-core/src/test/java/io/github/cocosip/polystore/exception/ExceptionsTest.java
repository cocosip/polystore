package io.github.cocosip.polystore.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ExceptionsTest {

    @Test
    void allExceptionsShouldExtendPolystoreException() {
        assertThat(new ContainerNotFoundException("c1")).isInstanceOf(PolystoreException.class);
        assertThat(new StorageProviderNotFoundException("minio")).isInstanceOf(PolystoreException.class);
        assertThat(new StorageFileNotFoundException("a.txt")).isInstanceOf(PolystoreException.class);
        assertThat(new StorageFileAlreadyExistsException("a.txt")).isInstanceOf(PolystoreException.class);
        assertThat(new TenantIdMissingException("c1")).isInstanceOf(PolystoreException.class);
        assertThat(new StorageOperationException("io failed", new RuntimeException("boom")))
                .isInstanceOf(PolystoreException.class);
    }

    @Test
    void containerNotFoundShouldCarryContainerName() {
        ContainerNotFoundException ex = new ContainerNotFoundException("missing");

        assertThat(ex.getContainerName()).isEqualTo("missing");
        assertThat(ex.getMessage()).contains("missing");
    }

    @Test
    void providerNotFoundShouldCarryProviderType() {
        StorageProviderNotFoundException ex = new StorageProviderNotFoundException("nope");

        assertThat(ex.getProviderType()).isEqualTo("nope");
        assertThat(ex.getMessage()).contains("nope");
    }

    @Test
    void fileExceptionsShouldCarryFileName() {
        assertThat(new StorageFileNotFoundException("a.txt").getFileName()).isEqualTo("a.txt");
        assertThat(new StorageFileNotFoundException("a.txt").getMessage()).contains("a.txt");
        assertThat(new StorageFileAlreadyExistsException("b.txt").getFileName()).isEqualTo("b.txt");
        assertThat(new StorageFileAlreadyExistsException("b.txt").getMessage()).contains("b.txt");
    }

    @Test
    void tenantMissingShouldCarryContainerName() {
        TenantIdMissingException ex = new TenantIdMissingException("dicom");

        assertThat(ex.getContainerName()).isEqualTo("dicom");
        assertThat(ex.getMessage())
                .contains("dicom")
                .contains("PATH_PREFIX")
                .contains("SaveArgs.tenantId")
                .contains("TenantIdSupplier");
    }

    @Test
    void operationExceptionShouldCarryCause() {
        RuntimeException cause = new RuntimeException("boom");
        StorageOperationException ex = new StorageOperationException("save failed", cause);

        assertThat(ex.getCause()).isSameAs(cause);
        assertThat(ex.getMessage()).isEqualTo("save failed");
    }
}
