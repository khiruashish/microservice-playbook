package dev.foodfleet.ch02.menu;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.GeneralCodingRules;

/**
 * Principles as executable fitness functions. A principle that isn't checked on every build is a wish;
 * these rules turn this chapter's principles into build failures.
 */
@AnalyzeClasses(packages = "dev.foodfleet.ch02.menu", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    /** Single responsibility per layer, dependencies pointing one way. */
    @ArchTest
    static final ArchRule layersPointOneWay = layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            .layer("API").definedBy("..menu.api..")
            .layer("Browse").definedBy("..menu.browse..")
            .layer("Catalog").definedBy("..menu.catalog..")
            .layer("Pricing").definedBy("..menu.pricing..")
            .whereLayer("API").mayNotBeAccessedByAnyLayer()
            .whereLayer("Browse").mayOnlyBeAccessedByLayers("API")
            .whereLayer("Catalog").mayOnlyBeAccessedByLayers("Browse")
            .whereLayer("Pricing").mayOnlyBeAccessedByLayers("Browse");

    /** Smart endpoints: the only code that knows pricing-service exists is the pricing adapter (and wiring). */
    @ArchTest
    static final ArchRule onlyThePricingAdapterTalksToPricingService = noClasses()
            .that().resideOutsideOfPackages("..menu.pricing..", "..menu.config..")
            .should().dependOnClassesThat().haveSimpleName("PricingClient");

    /** Remote calls go through declarative clients with group-level timeouts, never ad-hoc RestTemplates. */
    @ArchTest
    static final ArchRule noAdHocHttpClients = noClasses()
            .should().dependOnClassesThat().haveFullyQualifiedName("org.springframework.web.client.RestTemplate");

    /** Constructor injection keeps dependencies explicit and classes testable without Spring. */
    @ArchTest
    static final ArchRule noFieldInjection = GeneralCodingRules.NO_CLASSES_SHOULD_USE_FIELD_INJECTION;

    /** Logs go through SLF4J so they can be structured and correlated, never System.out. */
    @ArchTest
    static final ArchRule noStandardStreams = GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS;

    @ArchTest
    static final ArchRule noJavaUtilLogging = GeneralCodingRules.NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING;
}
