import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.TagFilter;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;

import java.util.ArrayList;
import java.util.List;

import static org.junit.platform.engine.discovery.DiscoverySelectors.selectPackage;

/**
 * Runs the tests of one or more tags with the JUnit Platform launcher and prints one line:
 * {@code <tag> tests=N failed=F ms=T failures=[...]}. Usage: RunSuites postgres h2 (-Dff.package=...)
 */
public final class RunSuites {

    public static void main(String[] args) {
        for (String tag : args) {
            List<String> failures = new ArrayList<>();
            int[] counts = new int[2];
            LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request().selectors(selectPackage(System.getProperty("ff.package", "dev.foodfleet.ch70")))
                    .filters(TagFilter.includeTags(tag)).build();
            long t = System.nanoTime();
            LauncherFactory.create().execute(request, new TestExecutionListener() {
                @Override
                public void executionFinished(TestIdentifier id, TestExecutionResult result) {
                    if (!id.isTest()) {
                        if (result.getStatus() != TestExecutionResult.Status.SUCCESSFUL) {
                            failures.add(id.getDisplayName() + " (container): " + result.getThrowable().map(Throwable::toString).orElse(""));
                        }
                        return;
                    }
                    counts[0]++;
                    if (result.getStatus() != TestExecutionResult.Status.SUCCESSFUL) {
                        counts[1]++;
                        failures.add(id.getDisplayName() + ": " + result.getThrowable().map(e -> e.toString().lines().findFirst().orElse("")).orElse(""));
                    }
                }
            });
            System.out.printf("%s tests=%d failed=%d ms=%d failures=%s%n", tag, counts[0], counts[1], (System.nanoTime() - t) / 1_000_000, failures);
        }
    }
}
