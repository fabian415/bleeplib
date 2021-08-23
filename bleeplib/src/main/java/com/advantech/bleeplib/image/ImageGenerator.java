package com.advantech.bleeplib.image;

import android.graphics.Bitmap;
import android.net.Uri;

import com.advantech.bleeplib.bean.PanelType;
import com.advantech.bleeplib.bean.TaskType;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * An image generator for internal usage.
 *
 * @author Fabian Chung
 * @version 1.0.0
 */
public class ImageGenerator {

    public static final int IMAGE_HEADER_LEN = 32;
    public static final int BLOCK_LEN = 240;
    public int total_block_number;
    private byte[] imageData;
    private int image_page;
    private int image_action;
    private Bitmap bitmap;
    private PanelType panelType;
    private int width;
    private int height;
    private static final int MIN = 0;
    private static final int MAX = 255;
    private static final int CENTER = 128;
    private TaskType taskType;
    private Uri packageUri;

    public ImageGenerator(TaskType taskType, byte[] packageData) {
        this.taskType = taskType;
        this.imageData = packageData;
    }

    public ImageGenerator(TaskType taskType, PanelType panelType, Bitmap bitmap, int image_page, int image_action) {
        this.taskType = taskType;
        this.panelType = panelType;
        this.bitmap = bitmap;
        this.image_page = image_page;
        this.image_action = image_action;
    }

    /**
     * Task type or image data is valid or not.
     *
     * @return
     */
    public boolean isValid() {
        if (taskType == TaskType.PUSH_IMAGE) {
            if (panelType == null || bitmap == null) return false;
            width = panelType.getWidth();
            height = panelType.getHeight();
            return (width == bitmap.getWidth() && height == bitmap.getHeight());
        } else {
            return imageData != null;
        }
    }

    /**
     * Execute task.
     *
     * @return
     */
    public boolean executeTask() {
        if (taskType == TaskType.PUSH_IMAGE) {
            switch (panelType) {
                case EPD250:
                    return generateEPD250();
                case EPD252:
                    return generateEPD252();
                case EPD353:
                    return generateEPD353();
                default:
                    return generateEPD250();
            }
        } else {
            return generatePackage();
        }
    }

    /**
     * Generate the OTA package.
     *
     * @return
     */
    private boolean generatePackage() {
        byte[] newImageData = preProcessFOTAImage(imageData);
        newImageData = addPaddingData(newImageData);
        total_block_number = (newImageData.length - IMAGE_HEADER_LEN + (BLOCK_LEN - 1)) / BLOCK_LEN;
        this.imageData = newImageData;
        return true;
    }

    /**
     * Generate the EPD-250 image.
     *
     * @return
     */
    private boolean generateEPD250() {
        RGBTriple[] palette = Dithering.bw;
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();

        byte[] imageData = new byte[width * height / 8];
        int m = 0;
        for (int i = 0; i < width; i++) {
            for (int j = (height - 1); j >= 0; j = j - 8) {
                byte total = 0;
                for (int k = 0; k < 8; k++) {
                    int pixel = bitmap.getPixel(i, j - k);
                    RGBTriple rgbTriple = Dithering.findNearestColor(pixel, palette);
                    int argb = (0 << 24) | (rgbTriple.channels[0] << 16) | (rgbTriple.channels[1] << 8) | rgbTriple.channels[2];
                    if ((argb & 0xff) == 0xff) { // white
                        int res = 1 << (7 - k);
                        total = (byte) (total + (byte) res);
                    }
                }
                imageData[m] = total;
                m++;
            }
        }

        imageData = preProcessImage(imageData, image_page, image_action);
        imageData = addPaddingData(imageData);
        total_block_number = (imageData.length - IMAGE_HEADER_LEN + (BLOCK_LEN - 1)) / BLOCK_LEN;
        this.imageData = imageData;
        return true;
    }

    /**
     * Generate the EPD-252 image.
     *
     * @return
     */
    private boolean generateEPD252() {
        RGBTriple[] palette = Dithering.bwr;
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();

        byte[] imageData = new byte[width * height / 4];
        int m = 0;
        for (int i = 0; i < width; i++) {
            for (int j = (height - 1); j >= 0; j = j - 8) {
                byte total = 0;
                byte total_2 = 0;
                for (int k = 0; k < 8; k++) {
                    int pixel = bitmap.getPixel(i, j - k);
                    RGBTriple rgbTriple = Dithering.findNearestColor(pixel, palette);
                    int argb = (0 << 24) | (rgbTriple.channels[0] << 16) | (rgbTriple.channels[1] << 8) | rgbTriple.channels[2];
                    if ((argb & 0xff) == 0xff) { // white
                        int res = 1 << (7 - k);
                        total = (byte) (total + (byte) res);
                    } else if ((argb & 0xff0000) == 0xff0000) { // red
                        int res = 1 << (7 - k);
                        total_2 = (byte) (total_2 + (byte) res);
                    }
                }
                imageData[m] = total;
                imageData[m + (width * height) / 8] = total_2;
                m++;
            }
        }

        imageData = preProcessImage(imageData, image_page, image_action);
        imageData = addPaddingData(imageData);
        total_block_number = (imageData.length - IMAGE_HEADER_LEN + (BLOCK_LEN - 1)) / BLOCK_LEN;
        this.imageData = imageData;
        return true;
    }

    /**
     * Generate the EPD-353 image.
     *
     * @return
     */
    private boolean generateEPD353() {
        RGBTriple[] palette = Dithering.sevenColor;
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();

        ByteArrayOutputStream os = new ByteArrayOutputStream();
        for (int y = 0; y < height; y++) {
            byte total = 0;
            int k = 0;
            for (int x = 0; x < width; x++) {
                int color = bitmap.getPixel(x, y);
                int b = color & 0xff;
                int g = (color & 0xff00) >> 8;
                int r = (color & 0xff0000) >> 16;
                while (true) {
                    if (r == MIN && g == MIN && b == MIN) { // Black
                        total |= 0b000 << ((1 - k) * 4);
                        break;
                    } else if (r == MIN && g == MAX && b == MIN) { // Green
                        total |= 0b010 << ((1 - k) * 4);
                        break;
                    } else if (r == MIN && g == MIN && b == MAX) { // Blue
                        total |= 0b011 << ((1 - k) * 4);
                        break;
                    } else if (r == MAX && g == MIN && b == MIN) { // Red
                        total |= 0b100 << ((1 - k) * 4);
                        break;
                    } else if (r == MAX && g == MAX && b == MIN) { // Yellow
                        total |= 0b101 << ((1 - k) * 4);
                        break;
                    } else if (r == MAX && g == CENTER && b == MIN) { // Orange
                        total |= 0b110 << ((1 - k) * 4);
                        break;
                    } else if (r == MAX && g == MAX && b == MAX) { // White
                        total |= 0b001 << ((1 - k) * 4);
                        break;
                    } else { // Others
                        RGBTriple rgbTriple = Dithering.findNearestColor(color, palette);
                        r = rgbTriple.channels[0];
                        g = rgbTriple.channels[1];
                        b = rgbTriple.channels[2];
                    }
                }

                k++;
                if (k >= 2) { // write to byte stream
                    os.write(total);
                    k = 0;
                    total = 0;
                }
            }
        }

        byte[] imageData = os.toByteArray();
        imageData = preProcessImage(imageData, image_page, image_action);
        imageData = addPaddingData(imageData);
        total_block_number = (imageData.length - IMAGE_HEADER_LEN + (BLOCK_LEN - 1)) / BLOCK_LEN;
        this.imageData = imageData;
        return true;
    }

    /**
     * Pre-process image and calculate the CRC value.
     *
     * @param imageData
     * @param image_page
     * @param image_action
     * @return
     */
    public byte[] preProcessImage(byte[] imageData, int image_page, int image_action) {
        byte[] newImageData = null;
        int image_data_len = imageData.length;
        byte[] oad_crc = new byte[]{(byte) 0xff};
        byte[] version = new byte[]{(byte) 0x00};
        // High byte 要放前面，Low byte 要放後面
        int length = image_data_len + IMAGE_HEADER_LEN;
        int image_type = 0x02;
        int compress_type = 0;
        int compress_len = 0;
        // image
        int epd_type = 0x20;
        int page_num = image_page - 1; // Page Number 0 is First Page
        int isRefresh = image_action;
        byte[] header = new byte[] {
            (byte) 0x00,
            (byte) ((length >> 0) & 0xff),
            (byte) ((length >> 8) & 0xff),
            (byte) ((length >> 16) & 0xff),
            (byte) ((length >> 24) & 0xff),
            (byte) image_type,
            (byte) compress_type,
            (byte) ((compress_len >> 0) & 0xff),
            (byte) ((compress_len >> 8) & 0xff),
            (byte) ((compress_len >> 16) & 0xff),
            (byte) ((compress_len >> 24) & 0xff),
            (byte) epd_type,
            (byte) page_num,
            (byte) isRefresh
        };

        byte[] header_reserve_array = new byte[16];
        for(int i = 0; i < header_reserve_array.length; i++) {
            header_reserve_array[i] = 0x00;
        }

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try {
            outputStream.write(header, 0, header.length);
            outputStream.write(header_reserve_array, 0, header_reserve_array.length);
            outputStream.write(imageData, 0, imageData.length);
            byte[] combined = outputStream.toByteArray();
            outputStream.flush();
            outputStream.close();

            // calculate CRC16
            int crc = crc16CCITT(combined);

            // High byte 要放前面，Low byte 要放後面
            oad_crc = new byte[]{
                (byte) ((crc >> 0) & 0xff),
                (byte) ((crc >> 8) & 0xff)
            };

            outputStream = new ByteArrayOutputStream();
            outputStream.write(oad_crc, 0, oad_crc.length);
            outputStream.write(combined, 0, combined.length);
            newImageData = outputStream.toByteArray();
            outputStream.flush();
            outputStream.close();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                outputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return newImageData;
    }

    /**
     * Pre-process OTA package data.
     *
     * @param imageData
     * @return
     */
    public byte[] preProcessFOTAImage(byte[] imageData) {
        byte[] newImageData = null;
        int image_data_len = imageData.length;
        byte[] oad_crc = new byte[]{(byte) 0xff};
        byte[] version = new byte[]{(byte) 0x00};
        // High byte 要放前面，Low byte 要放後面
        int length = image_data_len + IMAGE_HEADER_LEN;
        int image_type = 0x01;
        int compress_type = 0;
        int compress_len = 0;

        byte[] header = new byte[]{
                (byte) 0x00,
                (byte) ((length >> 0) & 0xff),
                (byte) ((length >> 8) & 0xff),
                (byte) ((length >> 16) & 0xff),
                (byte) ((length >> 24) & 0xff),
                (byte) image_type,
                (byte) compress_type,
                (byte) ((compress_len >> 0) & 0xff),
                (byte) ((compress_len >> 8) & 0xff),
                (byte) ((compress_len >> 16) & 0xff),
                (byte) ((compress_len >> 24) & 0xff),
                (byte) 0x00,
                (byte) 0x00,
                (byte) 0x00
        };
        byte[] header_reserve_array = new byte[16];
        for(int i = 0; i < header_reserve_array.length; i++) {
            header_reserve_array[i] = 0x00;
        }

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try {
            outputStream.write(header, 0, header.length);
            outputStream.write(header_reserve_array, 0, header_reserve_array.length);
            outputStream.write(imageData, 0, imageData.length);
            byte[] combined = outputStream.toByteArray();
            outputStream.flush();
            outputStream.close();

            // calculate CRC16
            int crc = crc16CCITT(combined);
            // High byte 要放前面，Low byte 要放後面
            oad_crc = new byte[] {
                    (byte) ((crc >> 0) & 0xff),
                    (byte) ((crc >> 8) & 0xff)
            };

            outputStream = new ByteArrayOutputStream();
            outputStream.write(oad_crc, 0, oad_crc.length);
            outputStream.write(combined, 0, combined.length);
            newImageData = outputStream.toByteArray();
            outputStream.flush();
            outputStream.close();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                outputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return newImageData;
    }

    private byte[] addPaddingData(byte[] imageData) {
        byte[] newImageData = null;
        int padding_len = (imageData.length - IMAGE_HEADER_LEN) % BLOCK_LEN;
        System.out.println("Padding Length : " + (BLOCK_LEN - padding_len));
        if (padding_len > 0) {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            try {
                outputStream.write(imageData, 0, imageData.length);
                outputStream.write(BLOCK_LEN - padding_len);
                newImageData = outputStream.toByteArray();
                outputStream.flush();
                outputStream.close();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                try {
                    outputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } else {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            try {
                outputStream.write(imageData, 0, imageData.length);
                newImageData = outputStream.toByteArray();
                outputStream.flush();
                outputStream.close();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                try {
                    outputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        return newImageData;
    }

    private static byte[] intToBytes(final int data) {
        return new byte[]{
                (byte) ((data >> 24) & 0xff),
                (byte) ((data >> 16) & 0xff),
                (byte) ((data >> 8) & 0xff),
                (byte) ((data >> 0) & 0xff)
        };
    }

    /******************************************************************************
     *  Compilation:  javac CRC16CCITT.java
     *  Execution:    java CRC16CCITT s
     *  Dependencies:
     *
     *  Reads in a sequence of bytes and prints out its 16 bit
     *  Cylcic Redundancy Check (CRC-CCIIT 0xFFFF).
     *
     *  1 + x + x^5 + x^12 + x^16 is irreducible polynomial.
     *
     *  % java CRC16-CCITT 123456789
     *  CRC16-CCITT = 29b1
     *
     ******************************************************************************/
    private int crc16CCITT(byte[] bytes) {
        int crc = 0x0000;          // initial value
        int polynomial = 0x1021;   // 0001 0000 0010 0001  (0, 5, 12)

        for (byte b : bytes) {
            for (int i = 0; i < 8; i++) {
                boolean bit = ((b >> (7 - i) & 1) == 1);
                boolean c15 = ((crc >> 15 & 1) == 1);
                crc <<= 1;
                if (c15 ^ bit) crc ^= polynomial;
            }
        }

        crc &= 0xffff;
        return crc;
    }


    /**
     * Get image data or package data.
     *
     * @return
     */
    public byte[] getImageData() {
        return imageData;
    }
}
