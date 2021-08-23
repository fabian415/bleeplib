package com.advantech.bleeplib.image;

/**
 * A java bean for storing RGB color information.
 *
 * @author Fabian Chung
 * @version 1.0.0
 */
public class RGBTriple {
    public final int[] channels;

    public RGBTriple() {
        channels = new int[3];
    }

    public RGBTriple(int R, int G, int B) {
        channels = new int[]{R, G, B};
    }
}

