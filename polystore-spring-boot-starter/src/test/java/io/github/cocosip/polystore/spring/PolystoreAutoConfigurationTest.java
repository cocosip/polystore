package io.github.cocosip.polystore.spring;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.cocosip.polystore.SaveArgs;
import io.github.cocosip.polystore.StorageManager;
import io.github.cocosip.polystore.TenantIdSupplier;
import io.github.cocosip.polystore.spring.event.FileDeletedEvent;
import io.github.cocosip.polystore.spring.event.FileSavedEvent;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class PolystoreAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(PolystoreAutoConfiguration.class))
            .withBean(TestStorageProvider.class)
            .withPropertyValues(
                    "polystore.containers[0].name=images",
                    "polystore.containers[0].type=test",
                    "polystore.containers[0].default=true");

    @Test
    void contextShouldExposeWorkingStorageManager() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(StorageManager.class);
            StorageManager manager = context.getBean(StorageManager.class);
            assertThat(manager.containerNames()).containsExactly("images");
            assertThat(manager.getDefaultContainer().getName()).isEqualTo("images");
            assertThat(manager.getDefaultContainer().getProviderType()).isEqualTo("test");

            manager.getContainer("images").save("a.txt", new ByteArrayInputStream(new byte[0]), SaveArgs.defaults());
            TestStorageProvider provider = context.getBean(TestStorageProvider.class);
            assertThat(provider.clientFor("images").store()).containsKey("a.txt");
        });
    }

    @Test
    void tenantIsolationShouldBindFromProperties() {
        runner.withPropertyValues("polystore.containers[0].tenant-isolation=PATH_PREFIX")
                .withBean(TenantIdSupplier.class, () -> () -> "tenant-a")
                .run(context -> {
                    StorageManager manager = context.getBean(StorageManager.class);
                    manager.getContainer("images")
                            .save("a.txt", new ByteArrayInputStream(new byte[0]), SaveArgs.defaults());

                    TestStorageProvider provider = context.getBean(TestStorageProvider.class);
                    assertThat(provider.clientFor("images").store()).containsKey("tenant-a/a.txt");
                });
    }

    @Test
    void storageEventsShouldReachApplicationListeners() {
        runner.withBean(EventCapture.class).run(context -> {
            StorageManager manager = context.getBean(StorageManager.class);
            manager.getContainer("images").save("a.txt", new ByteArrayInputStream(new byte[0]), SaveArgs.defaults());
            manager.getContainer("images").delete("a.txt");

            EventCapture capture = context.getBean(EventCapture.class);
            assertThat(capture.saved).hasSize(1);
            assertThat(capture.saved.get(0).getFileName()).isEqualTo("a.txt");
            assertThat(capture.deleted).hasSize(1);
            assertThat(capture.deleted.get(0).getFileName()).isEqualTo("a.txt");
        });
    }

    @Test
    void managerBeanShouldBeSkippedWhenUserProvidesOne() {
        runner.withBean("customManager", StorageManager.class, () -> PolystoreBuilder.builder()
                        .addProvider(new TestStorageProvider())
                        .addConfiguration(io.github.cocosip.polystore.ContainerConfiguration.builder()
                                .name("custom")
                                .type("test")
                                .build())
                        .build())
                .run(context -> {
                    assertThat(context).hasSingleBean(StorageManager.class);
                    assertThat(context.getBean(StorageManager.class).containerNames())
                            .containsExactly("custom");
                });
    }

    /** Captures storage events through the regular Spring event mechanism. */
    static class EventCapture {

        final List<FileSavedEvent> saved = new ArrayList<>();
        final List<FileDeletedEvent> deleted = new ArrayList<>();

        @org.springframework.context.event.EventListener
        void onSaved(FileSavedEvent event) {
            saved.add(event);
        }

        @org.springframework.context.event.EventListener
        void onDeleted(FileDeletedEvent event) {
            deleted.add(event);
        }
    }
}
