package org.awesoma.monitoring.config;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Set;
import org.awesoma.monitoring.application.usecase.PermissionPolicy;
import org.awesoma.monitoring.application.usecase.ReportDataService;
import org.springframework.transaction.interceptor.DefaultTransactionAttribute;
import org.springframework.transaction.interceptor.TransactionAttribute;
import org.springframework.transaction.interceptor.TransactionAttributeSource;
import org.springframework.util.ClassUtils;

/**
 * Transaction boundaries of every use case, kept out of the application layer. A public method
 * gets a read-write transaction unless it is known to only read, so a new method can never run
 * without a transaction by being forgotten.
 */
final class UseCaseTransactions implements TransactionAttributeSource {
    private static final String USE_CASE_PACKAGE = "org.awesoma.monitoring.application.usecase";
    private static final Set<Class<?>> READ_ONLY_USE_CASES = Set.of(PermissionPolicy.class, ReportDataService.class);
    private static final List<String> READ_PREFIXES = List.of("get", "list", "exists", "require");

    static boolean isUseCase(Class<?> type) {
        return USE_CASE_PACKAGE.equals(ClassUtils.getPackageName(ClassUtils.getUserClass(type)));
    }

    @Override
    public TransactionAttribute getTransactionAttribute(Method method, Class<?> targetClass) {
        Class<?> useCase = ClassUtils.getUserClass(targetClass == null ? method.getDeclaringClass() : targetClass);
        if (!isUseCase(useCase) || !Modifier.isPublic(method.getModifiers())
                || method.getDeclaringClass() == Object.class) {
            return null;
        }
        var attribute = new DefaultTransactionAttribute();
        attribute.setReadOnly(READ_ONLY_USE_CASES.contains(useCase)
                || READ_PREFIXES.stream().anyMatch(method.getName()::startsWith));
        return attribute;
    }
}
