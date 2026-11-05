package org.awesoma.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.UUID;
import org.awesoma.user.repository.UserRepository;
import org.awesoma.user.application.usecase.UserService;
import org.awesoma.user.domain.enums.UserRole;
import org.awesoma.user.support.AbstractIntegrationTest;
import org.awesoma.user.web.dto.user.UserCreateRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class UserLoggingIntegrationTest extends AbstractIntegrationTest {

    @Autowired private UserService users;
    @Autowired private UserRepository repository;
    @Autowired private PlatformTransactionManager transactionManager;

    private final Logger logger = (Logger) LoggerFactory.getLogger(UserService.class);
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();

    @BeforeEach
    void captureLogs() {
        logs.start();
        logger.addAppender(logs);
    }

    @AfterEach
    void stopCapture() {
        logger.detachAppender(logs);
        logs.stop();
    }

    @Test
    void aUserChangeIsLoggedOnlyAfterCommitWithoutPersonalData() {
        String email = UUID.randomUUID() + "@example.com";
        try {
            Long userId = new TransactionTemplate(transactionManager).execute(status -> {
                var created = users.create(email, "private-password", "Private Name", UserRole.VIEWER);
                assertThat(logs.list).isEmpty();
                return created.id();
            });

            assertThat(logs.list).singleElement().satisfies(event -> {
                assertThat(event.getFormattedMessage())
                        .contains("operation=CREATED", "user_id=" + userId, "role=VIEWER", "status=ACTIVE")
                        .doesNotContain(email, "private-password", "Private Name");
            });
        } finally {
            repository.findByEmail(email).ifPresent(repository::delete);
        }
    }

    @Test
    void aRolledBackUserChangeDoesNotProduceASuccessLog() {
        String email = UUID.randomUUID() + "@example.com";
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            users.create(email, "private-password", "Private Name", UserRole.VIEWER);
            status.setRollbackOnly();
        });

        assertThat(repository.findByEmail(email)).isEmpty();
        assertThat(logs.list).isEmpty();
    }
}
