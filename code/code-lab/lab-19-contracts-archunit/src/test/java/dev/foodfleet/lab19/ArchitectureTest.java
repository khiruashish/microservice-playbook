package dev.foodfleet.lab19;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.onionArchitecture;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.GeneralCodingRules;
import com.tngtech.archunit.library.freeze.FreezingArchRule;

/**
 * FoodFleet's fitness functions for a service's structure (ADR-009, ADR-073), checked on every build in a few
 * milliseconds. Chapter 65 planted 10 violations: these rules caught 8. A constant inlined by the compiler and a
 * class loaded by name are invisible to any bytecode rule; review and tests have to catch those.
 */
@AnalyzeClasses(packages = "dev.foodfleet.lab19", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule hexagonal = onionArchitecture()
            .domainModels("..menu.domain..")
            .applicationServices("..menu.application..")
            .adapter("web", "..menu.api..")
            .adapter("persistence", "..menu.infrastructure..");

    @ArchTest
    static final ArchRule domainIsFreeOfFrameworksAndIo = noClasses().that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage("org.springframework..", "jakarta..", "java.sql..", "java.net..")
            .because("the domain is plain Java: testable without a container, portable between adapters (ADR-009)");

    @ArchTest
    static final ArchRule noCycles = slices().matching("dev.foodfleet.lab19.(*)..").should().beFreeOfCycles();

    @ArchTest
    static final ArchRule noStandardStreams = GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS;

    @ArchTest
    static final ArchRule noFieldInjection = GeneralCodingRules.NO_CLASSES_SHOULD_USE_FIELD_INJECTION;

    /**
     * For an old service that already breaks a rule: record today's violations (in archunit_store/) and fail only on
     * new ones. Chapter 65: 5 frozen violations, a change adding 3 more, exactly those 3 reported.
     */
    @ArchTest
    static final ArchRule legacyLayering = FreezingArchRule.freeze(
            noClasses().that().resideInAPackage("..order..").should().dependOnClassesThat().resideInAPackage("..menu.infrastructure.."));
}
