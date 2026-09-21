package io.github.cocosip.polystore.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class ExactLengthInputStreamTest {
    @Test
    void shouldReadExactlyDeclaredLengthWithoutConsumingFollowingBytes() throws Exception {
        ByteArrayInputStream source = new ByteArrayInputStream(new byte[] {1, 2, 3, 4});
        ExactLengthInputStream bounded = new ExactLengthInputStream(source, 3);

        assertThat(bounded.readAllBytes()).containsExactly(1, 2, 3);
        bounded.verifyComplete();
        assertThat(source.read()).isEqualTo(4);
    }

    @Test
    void shouldFailOnEarlyEof() {
        ExactLengthInputStream bounded = new ExactLengthInputStream(new ByteArrayInputStream(new byte[] {1}), 2);

        assertThatThrownBy(bounded::readAllBytes)
                .isInstanceOf(IOException.class)
                .hasMessageContaining("expected 2 bytes");
    }

    @Test
    void closeShouldNotCloseTheCallerOwnedStream() throws Exception {
        ByteArrayInputStream source = new ByteArrayInputStream(new byte[] {1});
        ExactLengthInputStream bounded = new ExactLengthInputStream(source, 1);

        bounded.close();

        assertThat(source.read()).isEqualTo(1);
    }
}
