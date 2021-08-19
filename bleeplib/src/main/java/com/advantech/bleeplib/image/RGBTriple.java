package com.advantech.bleeplib.image;

public class RGBTriple {
    public final int[] channels;

    public RGBTriple() {
        channels = new int[3];
    }

    public RGBTriple(int R, int G, int B) {
        channels = new int[]{R, G, B};
    }
}

