package io.github.emmanuelcazarez.criteriaforge.core;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

class CoreArchitectureTest {

    private static final String CORE_PACKAGE =
        "io.github.emmanuelcazarez.criteriaforge.core";

    @Test
    void consumerApiRemainsInSingleCorePackage() {
        assertThat(QuerySpec.class.getPackageName()).isEqualTo(CORE_PACKAGE);
        assertThat(FilterExpression.class.getPackageName()).isEqualTo(CORE_PACKAGE);
        assertThat(QueryPolicy.class.getPackageName()).isEqualTo(CORE_PACKAGE);
        assertThat(CriteriaForgeException.class.getPackageName()).isEqualTo(CORE_PACKAGE);
    }

    @Test
    void coreRemainsTransportAndPersistenceIndependent() {
        var classes = new ClassFileImporter()
            .importPackages(CORE_PACKAGE);

        noClasses().should().dependOnClassesThat().resideInAnyPackage(
            "org.springframework..",
            "jakarta.persistence..",
            "jakarta.servlet..",
            "com.fasterxml.jackson..",
            "io.grpc..")
            .check(classes);
    }
}
