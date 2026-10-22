package dev.foodfleet.ch50.masking;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.encoder.Encoder;
import ch.qos.logback.core.encoder.EncoderBase;

import java.nio.charset.StandardCharsets;

/**
 * Wraps any Logback encoder (pattern, JSON, Spring Boot's structured formats) and masks the finished line, so every
 * field is covered: message, arguments, MDC, key-value pairs, exception messages and stack traces. It costs a scan
 * of every line and can't know about data it has no pattern for; it is the net under {@link Pii}, not a replacement.
 */
public final class MaskingEncoder extends EncoderBase<ILoggingEvent> {

    private Encoder<ILoggingEvent> delegate;

    public void setDelegate(Encoder<ILoggingEvent> delegate) {
        this.delegate = delegate;
    }

    @Override
    public void start() {
        if (delegate == null) {
            addError("MaskingEncoder needs a delegate encoder");
            return;
        }
        if (!delegate.isStarted()) {
            delegate.start();
        }
        super.start();
    }

    @Override
    public byte[] headerBytes() {
        return delegate.headerBytes();
    }

    @Override
    public byte[] encode(ILoggingEvent event) {
        byte[] raw = delegate.encode(event);
        return Masker.mask(new String(raw, StandardCharsets.UTF_8)).getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public byte[] footerBytes() {
        return delegate.footerBytes();
    }
}
