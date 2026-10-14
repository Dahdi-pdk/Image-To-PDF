package com.tes;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;

import java.io.ByteArrayOutputStream;

/**
 * Class untuk melakukan kompresi bitmap dengan berbagai metode
 */
public class BitmapCompressor {
    
    private CompressionSettings settings;

    public BitmapCompressor(CompressionSettings settings) {
        this.settings = settings;
    }

    /**
     * Kompresi bitmap dengan target ukuran tertentu
     */
    public Bitmap compressBitmap(Bitmap original, int targetWidth, int targetHeight) {
        if (original == null) {
            return null;
        }

        // FIX BUG 1: Jika level NONE, skip semua kompresi lossy.
        // Hanya lakukan resize ke targetWidth/Height dan grayscale jika diminta.
        if (settings.getLevel() == CompressionSettings.CompressionLevel.NONE) {
            Bitmap result = original;

            // Resize ke ukuran target di PDF (ini bukan kompresi, hanya scaling render)
            if (targetWidth > 0 && targetHeight > 0) {
                result = resizeBitmap(result, targetWidth, targetHeight);
            }

            // Grayscale tetap dihormati karena itu pilihan user yang terpisah
            if (settings.isConvertToGrayscale()) {
                result = convertToGrayscale(result);
            }

            // FIX BUG 1: Tidak memanggil applyJpegCompression sama sekali
            return result;
        }

        Bitmap result = original;

        // Step 1: Downsampling
        if (settings.getDownsamplingFactor() > 1) {  // FIX BUG 2: pakai > 1 bukan > 0
            result = downsampleBitmap(result, settings.getDownsamplingFactor());
        }

        // Step 2: Resize to target dimensions
        if (targetWidth > 0 && targetHeight > 0) {
            result = resizeBitmap(result, targetWidth, targetHeight);
        }

        // Step 3: Convert to grayscale
        if (settings.isConvertToGrayscale()) {
            result = convertToGrayscale(result);
        }

        // Step 4: JPEG compression — hanya dipanggil jika bukan NONE
        result = applyJpegCompression(result, settings.getJpegQuality());

        return result;
    }

    /**
     * Downsampling bitmap dengan faktor tertentu
     */
    private Bitmap downsampleBitmap(Bitmap source, int factor) {
        if (factor <= 1) {
            return source;
        }

        int newWidth = source.getWidth() / factor;
        int newHeight = source.getHeight() / factor;

        if (newWidth < 1) newWidth = 1;
        if (newHeight < 1) newHeight = 1;

        return Bitmap.createScaledBitmap(source, newWidth, newHeight, true);
    }

    /**
     * Resize bitmap ke ukuran target
     */
    private Bitmap resizeBitmap(Bitmap source, int targetWidth, int targetHeight) {
        if (source.getWidth() == targetWidth && source.getHeight() == targetHeight) {
            return source;
        }

        return Bitmap.createScaledBitmap(source, targetWidth, targetHeight, true);
    }

    /**
     * Konversi bitmap ke grayscale
     */
    private Bitmap convertToGrayscale(Bitmap source) {
        Bitmap grayscaleBitmap = Bitmap.createBitmap(
                source.getWidth(),
                source.getHeight(),
                Bitmap.Config.ARGB_8888
        );

        Canvas canvas = new Canvas(grayscaleBitmap);
        Paint paint = new Paint();

        ColorMatrix colorMatrix = new ColorMatrix();
        colorMatrix.setSaturation(0);

        ColorMatrixColorFilter filter = new ColorMatrixColorFilter(colorMatrix);
        paint.setColorFilter(filter);

        canvas.drawBitmap(source, 0, 0, paint);

        return grayscaleBitmap;
    }

    /**
     * Apply JPEG compression dengan kualitas tertentu.
     * JANGAN panggil method ini saat level NONE.
     */
    private Bitmap applyJpegCompression(Bitmap source, int quality) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        source.compress(Bitmap.CompressFormat.JPEG, quality, outputStream);

        byte[] byteArray = outputStream.toByteArray();

        return android.graphics.BitmapFactory.decodeByteArray(byteArray, 0, byteArray.length);
    }

    /**
     * Estimate ukuran file hasil kompresi
     */
    public long estimateCompressedSize(int originalWidth, int originalHeight) {
        int pixelCount = originalWidth * originalHeight;

        // FIX: Jika NONE, tidak ada downsampling
        int factor = settings.getDownsamplingFactor();
        if (factor > 1) {
            pixelCount = pixelCount / (factor * factor);
        }

        float qualityFactor = settings.getJpegQuality() / 100.0f;

        // Jika NONE, gunakan lossless estimate (PNG-like)
        long estimatedSize;
        if (settings.getLevel() == CompressionSettings.CompressionLevel.NONE) {
            estimatedSize = (long) (pixelCount * 3 * 0.8f); // PNG ~80% dari raw
        } else {
            estimatedSize = (long) (pixelCount * 3 * qualityFactor);
            estimatedSize = (long) (estimatedSize * 0.5f);
        }

        if (settings.isConvertToGrayscale()) {
            estimatedSize = (long) (estimatedSize * 0.33f);
        }

        return estimatedSize;
    }

    /**
     * Format ukuran file menjadi string yang mudah dibaca
     */
    public static String formatFileSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0f);
        } else {
            return String.format("%.2f MB", bytes / (1024.0f * 1024.0f));
        }
    }
}
