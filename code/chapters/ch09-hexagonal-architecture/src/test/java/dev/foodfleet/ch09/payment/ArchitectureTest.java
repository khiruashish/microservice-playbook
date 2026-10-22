package dev.foodfleet.ch09.payment;

import static com.tngtech.archunit.base.DescribedPredicate.alwaysTrue;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.assignableTo;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.onionArchitecture;

import com.tngtech.archunit.core.importer.ImportOption;
import dev.foodfleet.ch09.payment.application.port.out.PaymentGateway;
import dev.foodfleet.ch09.payment.application.port.out.PaymentRepository;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** The hexagon's shape, enforced on every build. */
@AnalyzeClasses(packages = "dev.foodfleet.ch09.payment", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    /**
     * ArchUnit's built-in onion rule: domain → nothing; application → domain; adapters → application
     * and domain, never each other. The composition root may wire everything.
     */
    @ArchTest
    static final ArchRule onion = onionArchitecture()
            .domainModels("..payment.domain..")
            .applicationServices("..payment.application..")
            .adapter("web", "..payment.adapter.in.web..")
            .adapter("events", "..payment.adapter.in.events..")
            .adapter("persistence", "..payment.adapter.out.persistence..")
            .adapter("gateway", "..payment.adapter.out.gateway..")
            .ignoreDependency(resideInAPackage("..payment.config.."), alwaysTrue());

    /** The core is plain Java: Spring, JDBC and HTTP live in adapters. */
    @ArchTest
    static final ArchRule coreIsFrameworkFree = noClasses()
            .that().resideInAnyPackage("..payment.domain..", "..payment.application..")
            .should().dependOnClassesThat().resideInAnyPackage("org.springframework..", "jakarta..", "java.sql..");

    /**
     * Driving adapters talk to use cases (port.in), never to the service class or the driven ports.
     * (They may catch the exceptions a driven port declares, to map them to HTTP status codes.)
     */
    @ArchTest
    static final ArchRule drivingAdaptersUseDrivingPorts = noClasses()
            .that().resideInAPackage("..payment.adapter.in..")
            .should().dependOnClassesThat(resideInAPackage("..application.service..")
                    .or(assignableTo(PaymentRepository.class))
                    .or(assignableTo(PaymentGateway.class)))
            .because("controllers and listeners call use cases; they don't orchestrate repositories or gateways");

    /** Ports are interfaces (plus the records and exceptions they declare). */
    @ArchTest
    static final ArchRule drivenPortsAreInterfaces = classes()
            .that().resideInAPackage("..application.port.out..")
            .and().areTopLevelClasses()
            .should().beInterfaces();
}
