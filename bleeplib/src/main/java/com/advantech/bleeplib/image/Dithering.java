package com.advantech.bleeplib.image;

import android.graphics.Bitmap;

/**
 * An utility class for pre-process image using Floyd-SteinBerg Dithering method.
 *
 * @author Fabian Chung
 * @version 1.0.0
 */
public class Dithering {
    public static final RGBTriple[] bw = { new RGBTriple(255, 255, 255), new RGBTriple(0, 0, 0) };
    public static final RGBTriple[] bwr = {new RGBTriple(255, 255, 255), new RGBTriple(0, 0, 0), new RGBTriple(255, 0, 0)};
    public static final RGBTriple[] sevenColor = {new RGBTriple(0, 0, 0), new RGBTriple(0, 0, 255), new RGBTriple(0, 255, 0),
            new RGBTriple(255, 0, 0), new RGBTriple(255, 128, 0), new RGBTriple(255, 255, 0), new RGBTriple(255, 255, 255)};
    public static final RGBTriple[] grayScale = {new RGBTriple(0, 0, 0), new RGBTriple(17, 17, 17), new RGBTriple(34, 34, 34),
            new RGBTriple(51, 51, 51), new RGBTriple(68, 68, 68), new RGBTriple(85, 85, 85), new RGBTriple(102, 102, 102),
            new RGBTriple(119, 119, 119), new RGBTriple(136, 136, 136), new RGBTriple(153, 153, 153), new RGBTriple(170, 170, 170),
            new RGBTriple(187, 187, 187), new RGBTriple(204, 204, 204), new RGBTriple(221, 221, 221), new RGBTriple(238, 238, 238),
            new RGBTriple(255, 255, 255)};

    /**
     * Pre-process image using Floyd-SteinBerg Dithering method.
     *
     * @param image
     * @param palette
     * @throws Exception
     */
    public static void applyFloydSteinbergDithering(final Bitmap image, RGBTriple[] palette) throws Exception {
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                final int argb = image.getPixel(x, y);
                RGBTriple rgbTriple = findNearestColor(argb, palette);
                final int nextArgb = (255 << 24) | (rgbTriple.channels[0] << 16) | (rgbTriple.channels[1] << 8) | rgbTriple.channels[2];
                image.setPixel(x, y, nextArgb);

                final int a = (argb >> 24) & 0xff;
                final int r = (argb >> 16) & 0xff;
                final int g = (argb >> 8) & 0xff;
                final int b = argb & 0xff;

                final int na = (nextArgb >> 24) & 0xff;
                final int nr = (nextArgb >> 16) & 0xff;
                final int ng = (nextArgb >> 8) & 0xff;
                final int nb = nextArgb & 0xff;

                final int errA = a - na;
                final int errR = r - nr;
                final int errG = g - ng;
                final int errB = b - nb;

                if (x + 1 < image.getWidth()) {
                    int update = adjustPixel(image.getPixel(x + 1, y), errA, errR, errG, errB, 7);
                    image.setPixel(x + 1, y, update);
                    if (y + 1 < image.getHeight()) {
                        update = adjustPixel(image.getPixel(x + 1, y + 1), errA, errR, errG, errB, 1);
                        image.setPixel(x + 1, y + 1, update);
                    }
                }
                if (y + 1 < image.getHeight()) {
                    int update = adjustPixel(image.getPixel(x, y + 1), errA, errR, errG, errB, 5);
                    image.setPixel(x, y + 1, update);
                    if (x - 1 >= 0) {
                        update = adjustPixel(image.getPixel(x - 1, y + 1), errA, errR, errG, errB, 3);
                        image.setPixel(x - 1, y + 1, update);
                    }
                }
            }
        }
    }

    private static int adjustPixel(final int argb, final int errA, final int errR, final int errG, final int errB, final int mul) {
        int a = (argb >> 24) & 0xff;
        int r = (argb >> 16) & 0xff;
        int g = (argb >> 8) & 0xff;
        int b = argb & 0xff;

        a += errA * mul >> 4;
        r += errR * mul >> 4;
        g += errG * mul >> 4;
        b += errB * mul >> 4;

        if (a < 0) {
            a = 0;
        } else if (a > 0xff) {
            a = 0xff;
        }
        if (r < 0) {
            r = 0;
        } else if (r > 0xff) {
            r = 0xff;
        }
        if (g < 0) {
            g = 0;
        } else if (g > 0xff) {
            g = 0xff;
        }
        if (b < 0) {
            b = 0;
        } else if (b > 0xff) {
            b = 0xff;
        }

        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /**
     * Find the nearest color using RGBTriple palette.
     *
     * @param argb      the source color in argb format
     * @param palette   the palette you want to find the nearest color to the source color
     * @return
     */
    public static RGBTriple findNearestColor(final int argb, RGBTriple[] palette) {
        final int a = (argb >> 24) & 0xff;
        final int r = (argb >> 16) & 0xff;
        final int g = (argb >> 8) & 0xff;
        final int b = argb & 0xff;

        int minDistanceSquared = 255*255 + 255*255 + 255*255 + 1;
        int bestIndex = 0;
        for (byte i = 0; i < palette.length; i++) {
            int Rdiff = r - palette[i].channels[0];
            int Gdiff = g - palette[i].channels[1];
            int Bdiff = b - palette[i].channels[2];
            int distanceSquared = Rdiff*Rdiff + Gdiff*Gdiff + Bdiff*Bdiff;
            if (distanceSquared < minDistanceSquared) {
                minDistanceSquared = distanceSquared;
                bestIndex = i;
            }
        }
        return palette[bestIndex];
    }

}
