package dev.foodfleet.ch16.branch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import dev.foodfleet.ch16.legacy.LegacyPricing;
import org.junit.jupiter.api.Test;

/**
 * Branch by abstraction, step 1, kept true: nothing calls the monolith's pricing code directly
 * except the adapter behind {@code PricingService}. New direct callers would be missed by the switch.
 */
class LegacyIsolationTest {

    private final JavaClasses classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("dev.foodfleet.ch16");

    @Test
    void onlyTheAdapterCallsLegacyPricing() {
        noClasses().that().doNotHaveSimpleName("LegacyPricingAdapter")
                .and().doNotHaveSimpleName("LegacyPricing")
                .should().dependOnClassesThat().areAssignableTo(LegacyPricing.class)
                .check(classes);
    }
}
