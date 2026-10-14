package com.tes;

import java.util.Locale;

/**
 * Class untuk menyimpan dan mengelola pengaturan kompresi gambar
 */
public class CompressionSettings {

    /**
     * Enum untuk level kompresi
     */
    public enum CompressionLevel {
        NONE("⚪ Tidak Dikompresi", 0, 100, 1.0f),
        LOW("🟢 Rendah - Cepat", 25, 90, 0.7f),
        MEDIUM("🔵 Sedang - Seimbang", 50, 75, 0.5f),
        HIGH("🟠 Tinggi - Hemat", 75, 60, 0.3f),
        MAXIMUM("🔴 Maksimal - Kecil", 100, 40, 0.15f);

        public final String displayName;
        public final int seekBarValue;
        public final int quality;
        public final float sizeFactor;

        CompressionLevel(String name, int sbValue, int qual, float factor) {
            this.displayName = name;
            this.seekBarValue = sbValue;
            this.quality = qual;
            this.sizeFactor = factor;
        }

        public static CompressionLevel fromSeekBarValue(int value) {
            if (value <= 0) return NONE;
            if (value <= 25) return LOW;
            if (value <= 50) return MEDIUM;
            if (value <= 75) return HIGH;
            return MAXIMUM;
        }
    }

    private CompressionLevel level = CompressionLevel.MEDIUM;
    private int jpegQuality = 75;
    private boolean convertToGrayscale = false;
    private boolean removeMetadata = true;
    private int downsamplingFactor = 2;

    public CompressionSettings() {
        updateFromLevel();
    }

    public void setSeekBarValue(int value) {
        this.level = CompressionLevel.fromSeekBarValue(value);
        updateFromLevel();
    }

    private void updateFromLevel() {
        this.jpegQuality = level.quality;
        if (level == CompressionLevel.NONE) {
            downsamplingFactor = 0;
            return;
        }

        switch (level) {
            case LOW:
                downsamplingFactor = 1;
                break;
            case MEDIUM:
                downsamplingFactor = 2;
                break;
            case HIGH:
                downsamplingFactor = 4;
                break;
            case MAXIMUM:
                downsamplingFactor = 8;
                break;
        }
    }

    public CompressionLevel getLevel() {
        return level;
    }

    public int getJpegQuality() {
        return jpegQuality;
    }

    public boolean isConvertToGrayscale() {
        return convertToGrayscale;
    }

    public void setConvertToGrayscale(boolean convert) {
        this.convertToGrayscale = convert;
    }

    public boolean isRemoveMetadata() {
        return removeMetadata;
    }

    public void setRemoveMetadata(boolean remove) {
        this.removeMetadata = remove;
    }

    public int getDownsamplingFactor() {
        return downsamplingFactor;
    }

    public String getCompressionSummary() {
        return String.format(Locale.US, "%s | Kualitas: %d%% | Downsample: %dx",
                level.displayName, jpegQuality, downsamplingFactor);
    }
}
