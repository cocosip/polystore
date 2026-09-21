package io.github.cocosip.polystore.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ConfigUtilsTest {

    @Test
    void normalizeKeyShouldLowercaseAndStripSeparators() {
        assertThat(ConfigUtils.normalizeKey("access-key")).isEqualTo("accesskey");
        assertThat(ConfigUtils.normalizeKey("accessKey")).isEqualTo("accesskey");
        assertThat(ConfigUtils.normalizeKey("ACCESS_KEY")).isEqualTo("accesskey");
        assertThat(ConfigUtils.normalizeKey("endpoint")).isEqualTo("endpoint");
        assertThat(ConfigUtils.normalizeKey(null)).isNull();
    }

    @Test
    void getShouldMatchRegardlessOfCaseOrSeparatorStyle() {
        Map<String, Object> properties = new HashMap<>();
        properties.put("access-key", "AK");

        assertThat(ConfigUtils.get(properties, "accessKey")).isEqualTo("AK");
        assertThat(ConfigUtils.get(properties, "ACCESS_KEY")).isEqualTo("AK");
        assertThat(ConfigUtils.get(properties, "secret-key")).isNull();
    }

    @Test
    void getShouldMatchTheReferenceQualifiedKeyStyle() {
        Map<String, Object> properties = new HashMap<>();
        properties.put("Minio.EndPoint", "http://minio:9000");
        properties.put("Aws.UseTemporaryFederatedCredentials", true);

        assertThat(ConfigUtils.get(properties, "endPoint")).isEqualTo("http://minio:9000");
        assertThat(ConfigUtils.get(properties, "useTemporaryFederatedCredentials"))
                .isEqualTo(true);
        assertThat(ConfigUtils.get(properties, "end")).isNull();
        assertThat(ConfigUtils.requireString(properties, "endPoint")).isEqualTo("http://minio:9000");
    }

    @Test
    void requireStringShouldReturnTrimmedValue() {
        Map<String, Object> properties = Map.of("endpoint", " http://x:9000 ");

        assertThat(ConfigUtils.requireString(properties, "endpoint")).isEqualTo("http://x:9000");
    }

    @Test
    void requireStringShouldRejectMissingAndBlank() {
        assertThatThrownBy(() -> ConfigUtils.requireString(Map.of(), "endpoint"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("endpoint");
        assertThatThrownBy(() -> ConfigUtils.requireString(Map.of("endpoint", "  "), "endpoint"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void optStringShouldFallBackOnAbsentOrBlank() {
        assertThat(ConfigUtils.optString(Map.of(), "region", "us-east-1")).isEqualTo("us-east-1");
        assertThat(ConfigUtils.optString(Map.of("region", " "), "region", "us-east-1"))
                .isEqualTo("us-east-1");
        assertThat(ConfigUtils.optString(Map.of("region", "cn-north-1"), "region", "us-east-1"))
                .isEqualTo("cn-north-1");
    }

    @Test
    void optIntAndOptLongShouldAcceptNumbersAndStrings() {
        assertThat(ConfigUtils.optInt(Map.of("urlExpiry", 3600), "urlExpiry", 0))
                .isEqualTo(3600);
        assertThat(ConfigUtils.optInt(Map.of("urlExpiry", "1800"), "urlExpiry", 0))
                .isEqualTo(1800);
        assertThat(ConfigUtils.optInt(Map.of("urlExpiry", "abc"), "urlExpiry", 42))
                .isEqualTo(42);
        assertThat(ConfigUtils.optInt(Map.of(), "urlExpiry", 42)).isEqualTo(42);
        assertThat(ConfigUtils.optLong(Map.of("size", 10L), "size", 0)).isEqualTo(10L);
    }

    @Test
    void optBooleanShouldAcceptBooleansAndStrings() {
        assertThat(ConfigUtils.optBoolean(Map.of("secure", true), "secure", false))
                .isTrue();
        assertThat(ConfigUtils.optBoolean(Map.of("secure", "true"), "secure", false))
                .isTrue();
        assertThat(ConfigUtils.optBoolean(Map.of("secure", "x"), "secure", true))
                .isFalse();
        assertThat(ConfigUtils.optBoolean(Map.of(), "secure", false)).isFalse();
    }
}
