package com.advantech.bleeplib.bean;

/**
 * An enum for panel-types of Advantech EPD device.
 *
 * @author Fabian Chung
 * @version 1.0.0
 */
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

    /**
     * Get serial number.
     *
     * @return  serial number
     */
    public int getId() {
        return id;
    }

    /**
     * Get the panel-type name.
     *
     * @return  the panel-type name
     */
    public String getValue() {
        return value;
    }

    /**
     * Get the width of this panel-type.
     *
     * @return  the width of this panel-type
     */
    public int getWidth() {
        return width;
    }

    /**
     * Get the height of this panel-type.
     *
     * @return the height of this panel-type
     */
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
