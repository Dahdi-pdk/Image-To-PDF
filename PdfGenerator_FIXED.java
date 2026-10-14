package com.tes;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Class untuk generate PDF dari list gambar
 * Menghandle layout, rendering, dan visual elements
 * 
 * FIX: Untuk level NONE, gunakan PNG encoding (lossless) sebelum menggambar ke PDF
 */
public class PdfGenerator {

    // Konstanta untuk ukuran A4 dalam points (1 point = 1/72 inch)
    private static final int A4_WIDTH_PT = 595;
    private static final int A4_HEIGHT_PT = 842;

    // Konversi cm ke points (1 cm = 28.3465 points)
    private static final float CM_TO_PT = 28.3465f;

    // Margin dan spacing
    private static final int MARGIN_PT = (int) (1.5 * CM_TO_PT); // 1.5cm margin
    private static final int SPACING_PT = (int) (0.5 * CM_TO_PT); // 0.5cm spacing

    private Context context;
    private CompressionSettings compressionSettings;
    private BitmapCompressor bitmapCompressor;
    private float imageWidthCm;

    // Visual elements flags
    private boolean showBorders = false;
    private boolean showDimensions = false;
    private boolean showRowInfo = false;
    private boolean showResolution = false;
    private boolean showScaleFactor = false;

    /**
     * Class untuk menyimpan informasi gambar
     */
    public static class ImageInfo {
        public Uri uri;
        public int originalWidth;
        public int originalHeight;
        public int targetWidth;
        public int targetHeight;
        public float scaleFactor;
        public int pageNumber;
        public int rowNumber;
        public float x;
        public float y;

        public ImageInfo(Uri uri, int width, int height) {
            this.uri = uri;
            this.originalWidth = width;
            this.originalHeight = height;
        }
    }

    /**
     * Class untuk menyimpan informasi baris (row)
     */
    public static class RowInfo {
        public int rowIndex;
        public float startX;
        public float startY;
        public int maxHeight = 0;
        public List<ImageInfo> images = new ArrayList<ImageInfo>();

        public RowInfo(int index, float x, float y) {
            this.rowIndex = index;
            this.startX = x;
            this.startY = y;
        }

        public void addImage(ImageInfo image) {
            images.add(image);
            if (image.targetHeight > maxHeight) {
                maxHeight = image.targetHeight;
            }
        }

        public int getNextY() {
            return (int) (startY + maxHeight + SPACING_PT);
        }
    }

    /**
     * Class untuk menyimpan informasi halaman
     */
    public static class PageInfoData {
        public int pageNumber;
        public int currentY = MARGIN_PT;
        public List<RowInfo> rows = new ArrayList<RowInfo>();

        public PageInfoData(int pageNum) {
            this.pageNumber = pageNum;
        }

        public void addRow(RowInfo row) {
            rows.add(row);
        }

        public int getNextRowY() {
            if (rows.isEmpty()) {
                return MARGIN_PT;
            }
            RowInfo lastRow = rows.get(rows.size() - 1);
            return lastRow.getNextY();
        }
    }

    /**
     * Interface untuk progress callback
     */
    public interface ProgressCallback {
        void onProgress(int current, int total, String message);
        boolean isCancelled();
    }

    /**
     * Constructor
     */
    public PdfGenerator(Context context, CompressionSettings settings, float imageWidthCm) {
        this.context = context;
        this.compressionSettings = settings;
        this.bitmapCompressor = new BitmapCompressor(settings);
        this.imageWidthCm = imageWidthCm;
    }

    /**
     * Set visual elements flags
     */
    public void setShowBorders(boolean show) {
        this.showBorders = show;
    }

    public void setShowDimensions(boolean show) {
        this.showDimensions = show;
    }

    public void setShowRowInfo(boolean show) {
        this.showRowInfo = show;
    }

    public void setShowResolution(boolean show) {
        this.showResolution = show;
    }

    public void setShowScaleFactor(boolean show) {
        this.showScaleFactor = show;
    }

    /**
     * Generate PDF dari list URI gambar
     */
    public PdfDocument generatePdf(List<Uri> imageUris, ProgressCallback callback) throws Exception {
        // Step 1: Load image dimensions
        if (callback != null) {
            callback.onProgress(0, imageUris.size(), "Memuat informasi gambar...");
        }

        List<ImageInfo> allImageInfos = new ArrayList<ImageInfo>();
        for (int i = 0; i < imageUris.size(); i++) {
            if (callback != null && callback.isCancelled()) {
                return null;
            }

            ImageInfo imageInfo = getImageDimensions(imageUris.get(i));
            if (imageInfo != null) {
                calculateTargetDimensions(imageInfo);
                allImageInfos.add(imageInfo);
            }

            if (callback != null) {
                callback.onProgress(i + 1, imageUris.size(), "Memuat gambar " + (i + 1) + "/" + imageUris.size());
            }
        }

        if (allImageInfos.isEmpty()) {
            throw new Exception("Tidak ada gambar yang valid");
        }

        // Step 2: Calculate layout (pages and rows)
        List<PageInfoData> pages = calculateLayout(allImageInfos);

        // Step 3: Create PDF
        PdfDocument pdfDocument = new PdfDocument();
        int totalPages = pages.size();

        for (int pageIndex = 0; pageIndex < pages.size(); pageIndex++) {
            if (callback != null && callback.isCancelled()) {
                pdfDocument.close();
                return null;
            }

            PageInfoData pageData = pages.get(pageIndex);

            if (callback != null) {
                callback.onProgress(pageIndex + 1, totalPages, 
                    "Membuat halaman " + (pageIndex + 1) + "/" + totalPages);
            }

            // Create page
            PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(
                    A4_WIDTH_PT, A4_HEIGHT_PT, pageIndex + 1).create();
            PdfDocument.Page page = pdfDocument.startPage(pageInfo);
            Canvas canvas = page.getCanvas();

            // Draw white background
            canvas.drawColor(Color.WHITE);

            // Draw header and footer
            drawPageHeader(canvas, pageData.pageNumber, totalPages);
            drawPageFooter(canvas);

            // Render all images in this page
            for (RowInfo row : pageData.rows) {
                for (ImageInfo image : row.images) {
                    renderImage(canvas, image, row);
                }
            }

            pdfDocument.finishPage(page);
        }

        if (callback != null) {
            callback.onProgress(totalPages, totalPages, "PDF selesai dibuat");
        }

        return pdfDocument;
    }

    /**
     * Get image dimensions dari URI
     */
    private ImageInfo getImageDimensions(Uri uri) {
        try {
            InputStream inputStream = context.getContentResolver().openInputStream(uri);
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(inputStream, null, options);
            inputStream.close();

            return new ImageInfo(uri, options.outWidth, options.outHeight);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Calculate target dimensions untuk gambar di PDF
     */
    private void calculateTargetDimensions(ImageInfo imageInfo) {
        float imageWidthPt = imageWidthCm * CM_TO_PT;
        int availableWidth = A4_WIDTH_PT - (2 * MARGIN_PT);

        if (imageWidthPt > availableWidth) {
            imageWidthPt = availableWidth;
        }

        float aspectRatio = (float) imageInfo.originalHeight / imageInfo.originalWidth;
        int targetWidthPt = (int) imageWidthPt;
        int targetHeightPt = (int) (targetWidthPt * aspectRatio);

        imageInfo.targetWidth = targetWidthPt;
        imageInfo.targetHeight = targetHeightPt;
        imageInfo.scaleFactor = (float) imageInfo.originalWidth / targetWidthPt;
    }

    /**
     * Calculate page layout (which images go on which page and row)
     */
    private List<PageInfoData> calculateLayout(List<ImageInfo> allImageInfos) {
        List<PageInfoData> pages = new ArrayList<PageInfoData>();
        PageInfoData currentPage = new PageInfoData(1);
        pages.add(currentPage);

        int currentX = MARGIN_PT;
        int currentRow = 0;
        RowInfo currentRowInfo = new RowInfo(currentRow, currentX, currentPage.currentY);
        currentPage.addRow(currentRowInfo);

        for (ImageInfo image : allImageInfos) {
            // Check if image fits in current row
            if (currentX + image.targetWidth > A4_WIDTH_PT - MARGIN_PT) {
                // New row needed
                currentRow++;
                int newY = currentPage.getNextRowY();

                // Check if new row fits in current page
                if (newY + image.targetHeight > A4_HEIGHT_PT - MARGIN_PT) {
                    // New page needed
                    currentPage = new PageInfoData(pages.size() + 1);
                    pages.add(currentPage);
                    currentRow = 0;
                    currentX = MARGIN_PT;
                    newY = MARGIN_PT;
                }

                currentX = MARGIN_PT;
                currentRowInfo = new RowInfo(currentRow, currentX, newY);
                currentPage.addRow(currentRowInfo);
            }

            image.pageNumber = currentPage.pageNumber;
            image.rowNumber = currentRow;
            image.x = currentX;
            image.y = currentRowInfo.startY;

            currentRowInfo.addImage(image);
            currentX += image.targetWidth + SPACING_PT;
        }

        return pages;
    }

    /**
     * Render single image on canvas
     * 
     * FIX: Untuk level NONE, encode ke PNG terlebih dahulu (lossless)
     */
    private void renderImage(Canvas canvas, ImageInfo image, RowInfo row) {
        try {
            // Load and compress bitmap
            InputStream inputStream = context.getContentResolver().openInputStream(image.uri);
            Bitmap originalBitmap = BitmapFactory.decodeStream(inputStream);
            inputStream.close();

            if (originalBitmap != null) {
                // Compress bitmap
                Bitmap compressedBitmap = bitmapCompressor.compressBitmap(
                        originalBitmap, image.targetWidth, image.targetHeight);

                // FIX: Untuk level NONE, encode ke PNG (lossless) sebelum menggambar ke PDF
                Bitmap finalBitmap = compressedBitmap;
                if (compressionSettings.getLevel() == CompressionSettings.CompressionLevel.NONE) {
                    finalBitmap = encodeToPngLossless(compressedBitmap);
                }

                // Draw bitmap
                drawScaledBitmap(canvas, finalBitmap,
                        image.x, image.y, image.targetWidth, image.targetHeight);

                // Render visual elements
                renderVisualElements(canvas, row, image, finalBitmap, true);

                // Cleanup
                if (finalBitmap != compressedBitmap) {
                    finalBitmap.recycle();
                }
                if (compressedBitmap != originalBitmap) {
                    compressedBitmap.recycle();
                }
                originalBitmap.recycle();
            }

        } catch (Exception e) {
            e.printStackTrace();
            // Render error placeholder
            renderVisualElements(canvas, row, image, null, false);
        }
    }

    /**
     * Encode bitmap ke PNG format (lossless)
     * Ini memastikan tidak ada kompresi lossy saat bitmap digambar ke PDF
     */
    private Bitmap encodeToPngLossless(Bitmap source) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        // PNG dengan quality 100 = lossless compression
        source.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
        byte[] byteArray = outputStream.toByteArray();
        return BitmapFactory.decodeByteArray(byteArray, 0, byteArray.length);
    }

    /**
     * Draw scaled bitmap on canvas
     */
    private void drawScaledBitmap(Canvas canvas, Bitmap bitmap,
                                   float left, float top, float targetWidth, float targetHeight) {
        Matrix matrix = new Matrix();
        float scaleX = targetWidth / bitmap.getWidth();
        float scaleY = targetHeight / bitmap.getHeight();
        matrix.setScale(scaleX, scaleY);
        matrix.postTranslate(left, top);

        Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
        paint.setAntiAlias(true);
        
        // FIX: Untuk level NONE, disable filtering untuk preservasi maksimal
        if (compressionSettings.getLevel() == CompressionSettings.CompressionLevel.NONE) {
            paint.setFilterBitmap(false);  // Disable bilinear filtering
            paint.setDither(false);        // Disable dithering
        } else {
            paint.setFilterBitmap(true);
            paint.setDither(true);
        }

        canvas.drawBitmap(bitmap, matrix, paint);
    }

    /**
     * Render visual elements (borders, dimensions, etc)
     */
    private void renderVisualElements(Canvas canvas, RowInfo row, ImageInfo image,
                                       Bitmap bitmap, boolean hasBitmap) {
        float textY = image.y + image.targetHeight + 12;

        // Border
        if (showBorders && hasBitmap) {
            Paint borderPaint = new Paint();
            borderPaint.setStyle(Paint.Style.STROKE);
            borderPaint.setColor(Color.LTGRAY);
            borderPaint.setStrokeWidth(0.5f);
            borderPaint.setAntiAlias(true);

            canvas.drawRect(image.x - 1, image.y - 1,
                    image.x + image.targetWidth + 1,
                    image.y + image.targetHeight + 1, borderPaint);
        }

        // Row info
        if (showRowInfo) {
            Paint rowPaint = new Paint();
            rowPaint.setColor(Color.parseColor("#FFB6C1"));
            rowPaint.setStyle(Paint.Style.STROKE);
            rowPaint.setStrokeWidth(0.3f);
            rowPaint.setAntiAlias(true);

            canvas.drawRect(MARGIN_PT, row.startY - 2,
                    A4_WIDTH_PT - MARGIN_PT,
                    row.startY + row.maxHeight + 2, rowPaint);

            Paint rowLabelPaint = new Paint();
            rowLabelPaint.setColor(Color.parseColor("#4682B4"));
            rowLabelPaint.setTextSize(7f);
            rowLabelPaint.setAntiAlias(true);

            String rowInfo = String.format(Locale.US, "Baris %d: Tinggi max = %.1f cm",
                    row.rowIndex + 1, row.maxHeight / CM_TO_PT);
            canvas.drawText(rowInfo, MARGIN_PT, row.startY - 8, rowLabelPaint);

            Paint rowNumberPaint = new Paint();
            rowNumberPaint.setColor(Color.parseColor("#4682B4"));
            rowNumberPaint.setTextSize(8f);
            rowNumberPaint.setAntiAlias(true);
            rowNumberPaint.setFakeBoldText(true);

            String rowLabel = "R" + (image.rowNumber + 1);
            canvas.drawText(rowLabel, image.x + 2, image.y + 12, rowNumberPaint);
        }

        // Dimensions
        if (showDimensions) {
            Paint dimPaint = new Paint();
            dimPaint.setColor(Color.BLACK);
            dimPaint.setTextSize(8f);
            dimPaint.setAntiAlias(true);

            String sizeInfo = String.format(Locale.US, "%.1f x %.1f cm",
                    image.targetWidth / CM_TO_PT,
                    image.targetHeight / CM_TO_PT);
            canvas.drawText(sizeInfo, image.x, textY, dimPaint);
            textY += 12;
        }

        // Resolution
        if (showResolution) {
            Paint resPaint = new Paint();
            resPaint.setColor(Color.parseColor("#2E7D32"));
            resPaint.setTextSize(7f);
            resPaint.setAntiAlias(true);

            String resInfo = String.format(Locale.US, "%dx%d px",
                    image.originalWidth, image.originalHeight);
            canvas.drawText(resInfo, image.x, textY, resPaint);
            textY += 12;
        }

        // Scale factor
        if (showScaleFactor) {
            Paint scalePaint = new Paint();
            scalePaint.setColor(Color.parseColor("#C2185B"));
            scalePaint.setTextSize(7f);
            scalePaint.setAntiAlias(true);

            float displayScale = 1 / image.scaleFactor;
            String scaleInfo;
            if (displayScale > 1) {
                scaleInfo = String.format(Locale.US, "Diperbesar: %.2fx", displayScale);
            } else if (displayScale < 1) {
                scaleInfo = String.format(Locale.US, "Diperkecil: %.2fx", displayScale);
            } else {
                scaleInfo = "Ukuran asli";
            }
            canvas.drawText(scaleInfo, image.x, textY, scalePaint);
        }
    }

    /**
     * Draw page header
     */
    private void drawPageHeader(Canvas canvas, int currentPage, int totalPages) {
        Paint headerPaint = new Paint();
        headerPaint.setColor(Color.BLACK);
        headerPaint.setTextSize(12f);
        headerPaint.setFakeBoldText(true);
        headerPaint.setAntiAlias(true);

        String headerText = String.format(Locale.US, "Halaman %d dari %d", currentPage, totalPages);
        canvas.drawText(headerText, MARGIN_PT, MARGIN_PT - 15, headerPaint);
    }

    /**
     * Draw page footer
     */
    private void drawPageFooter(Canvas canvas) {
        Paint footerPaint = new Paint();
        footerPaint.setColor(Color.DKGRAY);
        footerPaint.setTextSize(8f);
        footerPaint.setAntiAlias(true);

        String dateText = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US)
                .format(new Date());
        
        // FIX: Tambahkan indikator format untuk NONE
        String formatInfo = compressionSettings.getLevel() == CompressionSettings.CompressionLevel.NONE 
                ? compressionSettings.getLevel().displayName + " (PNG)" 
                : compressionSettings.getLevel().displayName;
        
        String footerText = String.format(Locale.US, 
                "Image to PDF • Generated: %s • Lebar: %.1f cm • Kompresi: %s",
                dateText, imageWidthCm, formatInfo);

        canvas.drawText(footerText, MARGIN_PT, A4_HEIGHT_PT - 20, footerPaint);
    }
}
