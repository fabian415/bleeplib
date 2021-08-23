package com.advantech.bleeplib.utils;

import android.graphics.Bitmap;
import android.graphics.Matrix;

import com.advantech.bleeplib.bean.PanelType;
import com.neovisionaries.bluetooth.ble.advertising.ADPayloadParser;
import com.neovisionaries.bluetooth.ble.advertising.ADStructure;

import java.util.List;

/**
 * Common is an utility class used for common purpose.
 *
 *  @author Fabian Chung
 *  @version 1.0.0
 */
public class Common {

    /**
     * Transform a byte array to an hexadecimal string
     *
     * @param byteArray     a byte array
     * @return              an hexadecimal string
     */
    public static String byteArrayToHexStr(byte[] byteArray) {
        if (byteArray == null) {
            return null;
        }
        char[] hexArray = "0123456789abcdef".toCharArray();
        char[] hexChars = new char[byteArray.length * 2];
        for (int j = 0; j < byteArray.length; j++) {
            int v = byteArray[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

    /**
     * Get PanelType by device name.
     *
     * @param deviceName    device name
     * @return              EPD panel-type {@see PanelType}
     */
    public static PanelType getPanelTypeByName(String deviceName) {
        PanelType panelType = null;
        if(deviceName != null) {
            String type = deviceName.split("Advantech_")[1];
            for(PanelType panel: PanelType.values()) {
                if(panel.getValue().equals(type)) {
                    panelType = panel;
                    break;
                }
            }
        }
        return panelType;
    }

    /**
     * Parse scan record into manufacturer data.
     *
     * @param scanRecord    scan record
     * @return              manufacturer data
     */
    public static byte[] parseManufacturerData(byte[] scanRecord) {
        byte[] result = null;
        List<ADStructure> structures = ADPayloadParser.getInstance().parse(scanRecord);
        for (ADStructure structure : structures) {
            byte type = Integer.valueOf(structure.getType()).byteValue();
            byte[] data = structure.getData();
            if (type == (byte) 0xFF) { // Manufacturer Specific Data
                result = data;
                break;
            }
        }
        return result;
    }

    /**
     * Resize an image using new width and height.
     *
     * @param bm            the source image in the Bitmap format
     * @param newWidth      new width
     * @param newHeight     new height
     * @return              the output image in the Bitmap format
     */
    public static Bitmap resizeBitmap(Bitmap bm, int newWidth, int newHeight) {
        int width = bm.getWidth();
        int height = bm.getHeight();
        float scaleWidth = ((float) newWidth) / width;
        float scaleHeight = ((float) newHeight) / height;
        // CREATE A MATRIX FOR THE MANIPULATION
        Matrix matrix = new Matrix();
        // RESIZE THE BIT MAP
        matrix.postScale(scaleWidth, scaleHeight);

        // "RECREATE" THE NEW BITMAP
        Bitmap resizedBitmap = Bitmap.createBitmap(
                bm, 0, 0, width, height, matrix, false);
        bm.recycle();
        return resizedBitmap;
    }

    /**
     * Rotate an image by degree.
     *
     * @param oriBitmap     the source image in the Bitmap format
     * @param degree        how much degrees to rotate the source image
     * @return              the output image in the Bitmap format
     */
    public static Bitmap rotateImage(Bitmap oriBitmap, int degree) {
        Bitmap rotatedBitmap = null;
        if (degree != 0) {
            Matrix matrix = new Matrix();
            matrix.postRotate(degree);
            rotatedBitmap = Bitmap.createBitmap(oriBitmap, 0, 0, oriBitmap.getWidth(), oriBitmap.getHeight(), matrix, false);
        } else {
            rotatedBitmap = oriBitmap.copy(Bitmap.Config.ARGB_8888, true);
        }
        oriBitmap.recycle();
        return rotatedBitmap;
    }
}
