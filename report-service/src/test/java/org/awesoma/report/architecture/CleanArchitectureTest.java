package org.awesoma.report.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

/**
 * Package names alone do not make the layers: dependencies must point inwards. The domain
 * knows only the JDK, and the use cases know the domain and their ports, not the frameworks
 * and libraries behind the adapters.
 */
class CleanArchitectureTest {
    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("org.awesoma.report");

    @Test
    void domainDependsOnlyOnTheJdk() {
        classes().that().resideInAPackage("..report.domain..")
                .should().onlyDependOnClassesThat().resideInAnyPackage("java..", "..report.domain..")
                .check(CLASSES);
    }

    @Test
    void applicationDoesNotKnowAdaptersOrFrameworks() {
        noClasses().that().resideInAPackage("..report.application..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..report.adapters..", "..report.config..",
                        "org.springframework.web..", "org.springframework.data..", "org.springframework.kafka..",
                        "org.springframework.security..", "jakarta.persistence..", "jakarta.servlet..",
                        "io.minio..", "feign..")
                .check(CLASSES);
    }

    @Test
    void adaptersDoNotDependOnEachOther() {
        for (String adapter : new String[] {"in", "out"}) {
            noClasses().that().resideInAPackage("..report.adapters." + adapter + "..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("..report.adapters." + (adapter.equals("in") ? "out" : "in") + "..")
                    .allowEmptyShould(true)
                    .check(CLASSES);
        }
    }
}
