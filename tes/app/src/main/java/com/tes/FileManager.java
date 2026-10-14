package com.tes;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.File;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Class untuk mengelola penyimpanan file PDF
 */
public class FileManager {

    /**
     * Result class untuk menyimpan informasi file yang tersimpan
     */
    public static class SaveResult {
        public Uri uri;
        public String fileName;
        public String displayPath;
        public long fileSize;
        public int pageCount;

        public SaveResult(Uri uri, String fileName, String displayPath, long fileSize, int pageCount) {
            this.uri = uri;
            this.fileName = fileName;
            this.displayPath = displayPath;
            this.fileSize = fileSize;
            this.pageCount = pageCount;
        }
    }

    /**
     * Save PDF document to storage
     */
    public static SaveResult savePdfDocument(Context context, PdfDocument pdfDocument) {
        try {
            ContentResolver resolver = context.getContentResolver();
            ContentValues values = new ContentValues();

            // Generate filename
            String fileName = "ImageToPDF_" + 
                    new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".pdf";
            
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
            values.put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf");

            String displayPath;
            
            // For Android 10 and above, use MediaStore
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.put(MediaStore.MediaColumns.RELATIVE_PATH,
                        Environment.DIRECTORY_DOCUMENTS + "/ImageToPDF");
                displayPath = "Documents/ImageToPDF/" + fileName;
            } else {
                // For older versions, use legacy external storage
                File dir = new File(Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_DOCUMENTS), "ImageToPDF");
                if (!dir.exists()) {
                    dir.mkdirs();
                }
                displayPath = dir.getAbsolutePath() + "/" + fileName;
            }

            Uri pdfUri = resolver.insert(MediaStore.Files.getContentUri("external"), values);

            if (pdfUri != null) {
                OutputStream outputStream = resolver.openOutputStream(pdfUri);
                pdfDocument.writeTo(outputStream);
                outputStream.close();

                // Get file size
                long fileSize = getFileSize(context, pdfUri);
                
                // Get page count
                int pageCount = pdfDocument.getPages().size();

                return new SaveResult(pdfUri, fileName, displayPath, fileSize, pageCount);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    /**
     * Get file size from URI
     */
    private static long getFileSize(Context context, Uri uri) {
        try {
            android.database.Cursor cursor = context.getContentResolver().query(
                    uri, null, null, null, null);
            
            if (cursor != null && cursor.moveToFirst()) {
                int sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE);
                if (sizeIndex != -1) {
                    long size = cursor.getLong(sizeIndex);
                    cursor.close();
                    return size;
                }
                cursor.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0;
    }

    /**
     * Format file size to human-readable string
     */
    public static String formatFileSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format(Locale.US, "%.1f KB", bytes / 1024.0f);
        } else {
            return String.format(Locale.US, "%.2f MB", bytes / (1024.0f * 1024.0f));
        }
    }

    /**
     * Get folder path for display
     */
    public static String getDisplayFolder() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return "Documents/ImageToPDF";
        } else {
            File dir = new File(Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOCUMENTS), "ImageToPDF");
            return dir.getAbsolutePath();
        }
    }
}
