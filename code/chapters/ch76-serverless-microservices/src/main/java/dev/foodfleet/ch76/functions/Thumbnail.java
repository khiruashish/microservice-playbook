package dev.foodfleet.ch76.functions;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.function.Function;

/**
 * A heavy function: renders a 1,600 × 1,200 menu photo stand-in, scales it to a 400 × 300 thumbnail with bicubic
 * interpolation and encodes it as PNG, as an upload handler would. Returns the PNG's size.
 */
public final class Thumbnail implements Function<String, String> {

    @Override
    public String apply(String json) {
        int seed = json.hashCode();
        BufferedImage photo = new BufferedImage(1_600, 1_200, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = photo.createGraphics();
        for (int i = 0; i < 400; i++) {
            int k = seed * 31 + i * 7_919;
            g.setColor(new Color((k >>> 8) & 0xFFFFFF));
            g.fillOval(Math.floorMod(k, 1_600), Math.floorMod(k >>> 3, 1_200), 40 + Math.floorMod(k, 160), 40 + Math.floorMod(k >>> 5, 120));
        }
        g.dispose();
        BufferedImage thumb = new BufferedImage(400, 300, BufferedImage.TYPE_INT_RGB);
        Graphics2D t = thumb.createGraphics();
        t.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        t.drawImage(photo, 0, 0, 400, 300, null);
        t.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            ImageIO.write(thumb, "png", out);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return "{\"pngBytes\":" + out.size() + "}";
    }
}
