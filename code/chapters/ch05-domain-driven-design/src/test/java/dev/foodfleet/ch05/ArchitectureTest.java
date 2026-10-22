package dev.foodfleet.ch05;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** DDD boundaries as fitness functions. */
@AnalyzeClasses(packages = "dev.foodfleet.ch05", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    /** The domain model is plain Java: no Spring, no persistence, no HTTP, no other context. */
    @ArchTest
    static final ArchRule domainDependsOnNothingButJava = classes()
            .that().resideInAPackage("..ordering.domain..")
            .should().onlyDependOnClassesThat().resideInAnyPackage("..ordering.domain..", "java..");

    /** Use cases depend on the domain and nothing technical. */
    @ArchTest
    static final ArchRule applicationIsFrameworkFree = classes()
            .that().resideInAPackage("..ordering.application..")
            .should().onlyDependOnClassesThat()
            .resideInAnyPackage("..ordering.application..", "..ordering.domain..", "java..");

    /** Only the anti-corruption layer may know the Catalog context's model. */
    @ArchTest
    static final ArchRule catalogModelStaysBehindTheAcl = noClasses()
            .that().resideInAPackage("..ordering..")
            .and().resideOutsideOfPackage("..ordering.infrastructure..")
            .should().dependOnClassesThat().resideInAPackage("dev.foodfleet.ch05.catalog..");

    /** The REST adapter talks to use cases, not to adapters. */
    @ArchTest
    static final ArchRule apiDoesNotUseInfrastructure = noClasses()
            .that().resideInAPackage("..ordering.api..")
            .should().dependOnClassesThat().resideInAPackage("..ordering.infrastructure..");
}
