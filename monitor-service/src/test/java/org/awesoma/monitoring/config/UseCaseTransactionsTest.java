package org.awesoma.monitoring.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Arrays;
import org.awesoma.monitoring.application.usecase.MonitorCheckService;
import org.awesoma.monitoring.application.usecase.MonitorService;
import org.awesoma.monitoring.application.usecase.PermissionPolicy;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.interceptor.TransactionAttribute;

class UseCaseTransactionsTest {
    private final UseCaseTransactions source = new UseCaseTransactions();

    @Test
    void readingMethodsAreReadOnly() throws Exception {
        assertThat(attribute(MonitorService.class, "get").isReadOnly()).isTrue();
        assertThat(attribute(MonitorService.class, "listByProject").isReadOnly()).isTrue();
        assertThat(attribute(PermissionPolicy.class, "allowed").isReadOnly()).isTrue();
    }

    @Test
    void anyOtherPublicMethodGetsAWriteTransaction() throws Exception {
        assertThat(attribute(MonitorService.class, "create").isReadOnly()).isFalse();
        assertThat(attribute(MonitorCheckService.class, "claimDueTargets").isReadOnly()).isFalse();
    }

    @Test
    void classesOutsideTheUseCasesGetNoTransaction() throws Exception {
        assertThat(source.getTransactionAttribute(String.class.getMethod("length"), String.class)).isNull();
        assertThat(source.getTransactionAttribute(Object.class.getMethod("toString"), MonitorService.class)).isNull();
    }

    private TransactionAttribute attribute(Class<?> type, String name) {
        Method method = Arrays.stream(type.getMethods()).filter(m -> m.getName().equals(name)).findFirst().orElseThrow();
        return source.getTransactionAttribute(method, type);
    }
}
