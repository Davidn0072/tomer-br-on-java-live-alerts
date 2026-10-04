package com.livealerts.server.storage;

import com.livealerts.server.protocol.ClearScreenMessage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MSSQLServerContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The exercise's required "integration test against MSSQL": boots the real JPA layer against an
 * actual SQL Server instance (Testcontainers) and proves a message written through
 * {@link MessageRepository} is genuinely persisted and readable back, schema included (relies
 * on the same {@code ddl-auto: update} that creates the schema on the real server's first
 * start). Named {@code *IT} on purpose — run via {@code mvn verify}, not the fast {@code mvn
 * test} unit run, since it needs Docker.
 */
@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class MessageRepositoryIT {

    @Container
    static final MSSQLServerContainer<?> mssql =
            new MSSQLServerContainer<>("mcr.microsoft.com/mssql/server:2022-latest").acceptLicense();

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mssql::getJdbcUrl);
        registry.add("spring.datasource.username", mssql::getUsername);
        registry.add("spring.datasource.password", mssql::getPassword);
    }

    @Autowired
    private MessageRepository repository;

    @Test
    void savedMessageIsRetrievableFromMssql() {
        StoredMessage saved = repository.save(
                new StoredMessage("emulator-1", "hello from the integration test", Instant.now()));

        Optional<StoredMessage> found = repository.findById(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getClientId()).isEqualTo("emulator-1");
        assertThat(found.get().getText()).isEqualTo("hello from the integration test");
        assertThat(found.get().getReceivedAt()).isNotNull();
    }

    @Test
    void savedClearScreenMessageHasNoTextAndTheClearScreenType() {
        StoredMessage saved = repository.save(
                new StoredMessage("emulator-1", null, Instant.now(), ClearScreenMessage.TYPE));

        Optional<StoredMessage> found = repository.findById(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getText()).isNull();
        assertThat(found.get().getType()).isEqualTo(ClearScreenMessage.TYPE);
    }

    @Test
    void findAllByOrderByReceivedAtDescReturnsMostRecentFirst() {
        Instant now = Instant.now();
        repository.save(new StoredMessage("emulator-1", "older", now.minusSeconds(60)));
        repository.save(new StoredMessage("emulator-1", "newer", now));

        List<StoredMessage> all = repository.findAllByOrderByReceivedAtDesc();

        assertThat(all).hasSizeGreaterThanOrEqualTo(2);
        assertThat(all.get(0).getText()).isEqualTo("newer");
    }
}
