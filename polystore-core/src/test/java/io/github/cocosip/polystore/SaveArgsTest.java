package io.github.cocosip.polystore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SaveArgsTest {

    @Test
    void defaultsShouldOverwriteWithoutContentTypeOrMetadata() {
        SaveArgs args = SaveArgs.defaults();

        assertThat(args.isOverwrite()).isTrue();
        assertThat(args.getContentType()).isNull();
        assertThat(args.getMetadata()).isEmpty();
        assertThat(args.getTenantId()).isNull();
    }

    @Test
    void builderShouldSetAllFields() {
        SaveArgs args = SaveArgs.builder()
                .contentType("application/dicom")
                .overwrite(false)
                .tenantId("tenant-abc")
                .metadata(Map.of("patientId", "P-1"))
                .build();

        assertThat(args.getContentType()).isEqualTo("application/dicom");
        assertThat(args.isOverwrite()).isFalse();
        assertThat(args.getTenantId()).isEqualTo("tenant-abc");
        assertThat(args.getMetadata()).containsEntry("patientId", "P-1");
    }

    @Test
    void metadataShouldBeDefensivelyCopied() {
        Map<String, String> source = new HashMap<>();
        source.put("k", "v");

        SaveArgs args = SaveArgs.builder().metadata(source).build();
        source.put("k", "changed");

        assertThat(args.getMetadata()).containsEntry("k", "v");
    }

    @Test
    void metadataShouldBeUnmodifiable() {
        SaveArgs args = SaveArgs.builder().metadata(Map.of("k", "v")).build();

        assertThatThrownBy(() -> args.getMetadata().put("x", "y")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void nullMetadataShouldBeTreatedAsEmpty() {
        SaveArgs args = SaveArgs.builder().metadata(null).build();

        assertThat(args.getMetadata()).isEmpty();
    }

    @Test
    void toStringShouldContainKeyFields() {
        SaveArgs args = SaveArgs.builder().tenantId("t1").build();

        assertThat(args.toString()).contains("overwrite=true").contains("tenantId=t1");
    }
}
