package io.github.cocosip.polystore;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class UrlArgsTest {

    @Test
    void defaultsShouldExpireAfterOneHourWithAttachmentDisposition() {
        UrlArgs args = UrlArgs.defaults();

        assertThat(args.getExpiry()).isEqualTo(Duration.ofHours(1));
        assertThat(args.getExpiry()).isEqualTo(UrlArgs.DEFAULT_EXPIRY);
        assertThat(args.isInline()).isFalse();
    }

    @Test
    void builderShouldSetAllFields() {
        UrlArgs args =
                UrlArgs.builder().expiry(Duration.ofMinutes(5)).inline(true).build();

        assertThat(args.getExpiry()).isEqualTo(Duration.ofMinutes(5));
        assertThat(args.isInline()).isTrue();
    }

    @Test
    void toStringShouldContainKeyFields() {
        assertThat(UrlArgs.defaults().toString()).contains("PT1H").contains("inline=false");
    }
}
