package com.advantech.bleeplib.bean;

public enum PanelType {

    EPD250(0, "EPD-250", 296, 128),
    EPD252(1, "EPD-252", 296, 128),
    EPD353(2, "EPD-353", 600, 448);

    private int id;
    private final String value;
    private final int width;
    private final int height;

    private PanelType(int id, String value, int width, int height) {
        this.id = id;
        this.value = value;
        this.width = width;
        this.height = height;
    }

    public int getId() {
        return id;
    }

    public String getValue() {
        return value;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    @Override
    public String toString() {
        return "PanelType{" +
                "id=" + id +
                ", value='" + value + '\'' +
                ", width=" + width +
                ", height=" + height +
                '}';
    }
}
