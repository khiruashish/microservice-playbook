import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.JsonEncoder;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.FileAppender;
import ch.qos.logback.core.encoder.Encoder;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Logback set up in code, the way logback.xml or Spring Boot would, for the chapter's experiments. */
final class Lab {

    private Lab() {
    }

    static LoggerContext context() {
        return (LoggerContext) LoggerFactory.getILoggerFactory();
    }

    /** Replaces the root logger's appenders with {@code appender}, at INFO. */
    static void root(Appender<ILoggingEvent> appender) {
        Logger root = context().getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        root.detachAndStopAllAppenders();
        root.setLevel(Level.INFO);
        root.addAppender(appender);
    }

    static JsonEncoder json() {
        JsonEncoder e = new JsonEncoder();
        e.setContext(context());
        e.start();
        return e;
    }

    static PatternLayoutEncoder pattern(String pattern) {
        PatternLayoutEncoder e = new PatternLayoutEncoder();
        e.setContext(context());
        e.setPattern(pattern);
        e.start();
        return e;
    }

    static FileAppender<ILoggingEvent> file(Path path, Encoder<ILoggingEvent> encoder, boolean immediateFlush) {
        FileAppender<ILoggingEvent> a = new FileAppender<>();
        a.setContext(context());
        a.setName("file");
        a.setFile(path.toString());
        a.setAppend(false);
        a.setImmediateFlush(immediateFlush);
        a.setEncoder(encoder);
        a.start();
        return a;
    }

    static double percentile(List<Double> values, double p) {
        if (values.isEmpty()) {
            return Double.NaN;
        }
        List<Double> s = new ArrayList<>(values);
        Collections.sort(s);
        int i = (int) Math.ceil(p / 100.0 * s.size()) - 1;
        return s.get(Math.max(0, Math.min(s.size() - 1, i)));
    }
}
