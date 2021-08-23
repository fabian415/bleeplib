package com.advantech.bleeplib.image;

import android.graphics.Bitmap;

/**
 * An utility class for pre-process image using BSC adjuster.
 *
 * @author Fabian Chung
 * @version 1.0.0
 */
public class BSCAdjuster {

    /**
     * Adjust image using saturation, brightness, and contrast values.
     *
     * @param src
     * @param saturation
     * @param brightness
     * @param contrast
     */
    public static void transform(Bitmap src, double saturation, double brightness, double contrast) {
        // 調整各系數取值范圍
        saturation = (1.0 + saturation / 100.0);
        brightness = (1.0 + brightness / 100.0);
        contrast = (1.0 + contrast / 100.0);

        int width = src.getWidth();
        int height = src.getHeight();
        int[] inpixels = new int[width * height];
        int[] outpixels = new int[width * height];

        getRGB(src, 0, 0, width, height, inpixels);

        int index = 0;
        for (int row = 0; row < height; row++) {
            int ta = 0, tr = 0, tg = 0, tb = 0;
            for (int col = 0; col < width; col++) {
                index = row * width + col;
                ta = (inpixels[index] >> 24) & 0xff;
                tr = (inpixels[index] >> 16) & 0xff;
                tg = (inpixels[index] >> 8) & 0xff;
                tb = inpixels[index] & 0xff;
                // RGB轉換為HSL色彩空間
                double[] hsl = rgb2hsl(new int[]{tr, tg, tb});

                // 調整飽和度
                hsl[1] = hsl[1] * saturation;
                if (hsl[1] < 0.0) {
                    hsl[1] = 0.0;
                }
                if (hsl[1] > 255.0) {
                    hsl[1] = 255.0;
                }

                // 調整亮度
                hsl[2] = hsl[2] * brightness;
                if (hsl[2] < 0.0) {
                    hsl[2] = 0.0;
                }
                if (hsl[2] > 255.0) {
                    hsl[2] = 255.0;
                }
                // HSL轉換為rgb空間
                int[] rgb = hsl2rgb(hsl);
                tr = clamp(rgb[0]);
                tg = clamp(rgb[1]);
                tb = clamp(rgb[2]);

                // 調整對比度
                double cr = ((tr / 255.0d) - 0.5d) * contrast;
                double cg = ((tg / 255.0d) - 0.5d) * contrast;
                double cb = ((tb / 255.0d) - 0.5d) * contrast;
                // 輸出RGB值
                tr = (int) ((cr + 0.5f) * 255.0f);
                tg = (int) ((cg + 0.5f) * 255.0f);
                tb = (int) ((cb + 0.5f) * 255.0f);

                outpixels[index] = (ta << 24) | (clamp(tr) << 16) | (clamp(tg) << 8) | clamp(tb);
            }
        }
        setRGB(src, 0, 0, width, height, outpixels);
    }

    private static int clamp(int value) {
        return value > 255 ? 255 : ((value < 0) ? 0 : value);
    }

    // 讀取像素數據
    private static void getRGB(Bitmap image, int x, int y, int width, int height, int[] pixels) {
        image.getPixels(pixels, 0, width, x, y, width, height);
    }

    // 寫入像素數據
    private static void setRGB(Bitmap image, int x, int y, int width, int height, int[] pixels) {
        image.setPixels(pixels, 0, width, x, y, width, height);
    }

    /**
     * RGB to HSL.
     *
     * @param rgb
     * @return
     */
    public static double[] rgb2hsl(int[] rgb) {
        double max = Math.max(Math.max(rgb[0], rgb[1]), rgb[2]); // 0xdd = 221
        double delta = max - Math.min(Math.min(rgb[0], rgb[1]), rgb[2]); // 153
        double h = 0;
        int s = 0;
        int l = (int) Math.round(max * 100d / 255d); // 87 ok
        if (max != 0) {
            s = (int) Math.round(delta * 100d / max); // 69 ok
            if (max == rgb[0]) {
                h = (rgb[1] - rgb[2]) / delta;
            } else if (max == rgb[1]) {
                h = (rgb[2] - rgb[0]) / delta + 2d;
            } else {
                h = (rgb[0] - rgb[1]) / delta + 4d; // 4.8888888888
            } // from w ww. ja v a 2 s . c om
            h = Math.min(Math.round(h * 60d), 360d); // 293
            if (h < 0d) {
                h += 360d;
            }
        }
        return new double[]{h, s, l};
    }

    /**
     * HSL to RGB.
     *
     * @param hsl
     * @return
     */
    public static int[] hsl2rgb(double[] hsl) {
        double h = hsl[0] / 360d;
        double s = hsl[1] / 100d;
        double l = hsl[2] / 100d;
        double r = 0d;
        double g = 0d;
        double b;

        if (s > 0d) {
            if (h >= 1d) {
                h = 0d;
            }

            h = h * 6d;
            double f = h - Math.floor(h);
            double a = Math.round(l * 255d * (1d - s));
            b = Math.round(l * 255d * (1d - (s * f)));
            double c = Math.round(l * 255d * (1d - (s * (1d - f))));
            l = Math.round(l * 255d);

            switch ((int) Math.floor(h)) {
                case 0:
                    r = l;
                    g = c;
                    b = a;
                    break;
                case 1:
                    r = b;
                    g = l;
                    b = a;
                    break;
                case 2:
                    r = a;
                    g = l;
                    b = c;
                    break;
                case 3:
                    r = a;
                    g = b;
                    b = l;
                    break;
                case 4:
                    r = c;
                    g = a;
                    b = l;
                    break;
                case 5:
                    r = l;
                    g = a;
                    break;
            }
            return new int[]{(int) Math.round(r), (int) Math.round(g), (int) Math.round(b)};
        }

        l = Math.round(l * 255d);
        return new int[]{(int) l, (int) l, (int) l};
    }

}
