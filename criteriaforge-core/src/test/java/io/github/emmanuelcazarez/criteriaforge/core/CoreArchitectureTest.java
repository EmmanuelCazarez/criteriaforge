package io.github.emmanuelcazarez.criteriaforge.core;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

class CoreArchitectureTest {

    @Test
    void coreRemainsTransportAndPersistenceIndependent() {
        var classes = new ClassFileImporter()
            .importPackages("io.github.emmanuelcazarez.criteriaforge.core");

        noClasses().should().dependOnClassesThat().resideInAnyPackage(
            "org.springframework..",
            "jakarta.persistence..",
            "jakarta.servlet..",
            "com.fasterxml.jackson..",
            "io.grpc..")
            .check(classes);
    }
}
