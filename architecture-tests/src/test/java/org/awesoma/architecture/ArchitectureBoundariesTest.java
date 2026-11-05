package org.awesoma.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Verifies dependencies of compiled bytecode, including annotations and method signatures. */
class ArchitectureBoundariesTest {
    private static final List<Service> SERVICES = List.of(
            new Service("user-service", "org.awesoma.user"),
            new Service("monitor-service", "org.awesoma.monitoring"),
            new Service("check-service", "org.awesoma.check"),
            new Service("notification-service", "org.awesoma.notification"),
            new Service("report-service", "org.awesoma.report"));

    private static final String[] FRAMEWORKS = {
        "org.springframework..", "jakarta..", "org.hibernate..", "java.sql..", "javax.sql..",
        "org.apache.kafka..", "io.minio..", "feign..", "com.fasterxml.jackson..",
        "org.springframework.security..", "org.awesoma.security..", "java.net.http..", "java.nio.file.."
    };

    @Test void userServiceBoundaries() { verifyService(SERVICES.get(0)); }
    @Test void monitorServiceBoundaries() { verifyService(SERVICES.get(1)); }
    @Test void checkServiceBoundaries() { verifyService(SERVICES.get(2)); }
    @Test void notificationServiceBoundaries() { verifyService(SERVICES.get(3)); }
    @Test void reportServiceBoundaries() { verifyService(SERVICES.get(4)); }

    private void verifyService(Service service) {
            JavaClasses classes = load(service.module());
            assertFalse(classes.stream().noneMatch(c -> c.getPackageName().startsWith(service.root() + ".domain")),
                    service.module() + " must contain a domain layer");
            assertFalse(classes.stream().noneMatch(c -> c.getPackageName().startsWith(service.root() + ".application")),
                    service.module() + " must contain an application layer");
            assertFalse(classes.stream().anyMatch(c -> c.getPackageName().startsWith(service.root() + ".service")),
                    service.module() + " must not leave business services in the legacy service package");
            String domain = service.root() + ".domain..";
            String application = service.root() + ".application..";
            String[] outer = {service.root() + ".adapters..", service.root() + ".web..",
                    service.root() + ".repository..", service.root() + ".config..",
                    service.root() + ".client..", service.root() + ".integration..",
                    service.root() + ".security..", service.root() + ".probe..",
                    service.root() + ".delivery..", service.root() + ".support.."};
            noClasses().that().resideInAnyPackage(domain, application)
                    .should().dependOnClassesThat().resideInAnyPackage(FRAMEWORKS)
                    .because("business rules must not depend on frameworks").check(classes);
            for (String ioType : List.of("java.io.File", "java.io.FileInputStream", "java.io.FileOutputStream",
                    "java.io.RandomAccessFile", "java.nio.channels.FileChannel", "java.net.URL",
                    "java.net.URLConnection", "java.net.HttpURLConnection", "java.net.Socket",
                    "java.net.ServerSocket", "java.net.DatagramSocket")) {
                noClasses().that().resideInAnyPackage(domain, application)
                        .should().dependOnClassesThat().haveFullyQualifiedName(ioType)
                        .because("concrete filesystem and network operations belong in adapters").check(classes);
            }
            noClasses().that().resideInAnyPackage(domain, application)
                    .should().dependOnClassesThat().resideInAnyPackage(outer)
                    .because("business rules must depend on ports, not adapters").check(classes);
            noClasses().that().resideInAPackage(domain).should().dependOnClassesThat()
                    .resideInAPackage(application).because("domain is the innermost layer").check(classes);
    }

    @Test
    void sharedContractsRemainFrameworkIndependent() {
        JavaClasses classes = load("integration-contracts");
        assertFalse(classes.isEmpty(), "shared contracts must not be empty");
        noClasses().should().dependOnClassesThat().resideInAnyPackage(FRAMEWORKS).check(classes);
    }

    private static JavaClasses load(String module) {
        Path path = Path.of(System.getProperty("architecture.root"), module, "build", "classes", "java", "main");
        return new ClassFileImporter().importPath(path);
    }

    private record Service(String module, String root) {}
}
