package com.aldi.imgtopdf;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.graphics.pdf.PdfDocument.Page;
import android.graphics.pdf.PdfDocument.PageInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class convert extends Activity {

    private static final int PICK_IMAGES_CODE = 100;
    private ArrayList<Uri> selectedImageUris = new ArrayList<>();
    private TextView tvStatus;
    private Button btnGenerate;
    private ProgressBar progressBar;
    private TextView tvProgressPercent;
    private LinearLayout progressContainer;

    // Switch controls untuk visual elements
    private Switch switchShowBorders;
    private Switch switchShowDimensions;
    private Switch switchShowRowInfo;
    private Switch switchShowResolution;
    private Switch switchShowScaleFactor;
    private Switch switchShowAll;

    // Kompresi controls
    private SeekBar seekBarCompression;
    private TextView tvCompressionValue;
    private TextView tvCompressionLabel;
    private TextView tvEstimatedSize;
    private CheckBox chkGrayscale;
    private CheckBox chkRemoveMetadata;
    private CompressionSettings currentCompressionSettings;

    // ==================== VARIABEL UNTUK LEBAR GAMBAR ====================
    private SeekBar seekBarImageWidth;
    private TextView tvImageWidthValue;
    private TextView tvImageWidthLabel;
    private float currentImageWidthCm = 3.0f; // Default 3cm
    private static final float MIN_WIDTH_CM = 0.5f;
    private static final float MAX_WIDTH_CM = 10.0f;
    private static final float DEFAULT_WIDTH_CM = 3.0f;
    // =====================================================================

    // Konstanta untuk ukuran A4 dalam points (1 point = 1/72 inch)
    private static final int A4_WIDTH_PT = 595;
    private static final int A4_HEIGHT_PT = 842;

    // Konversi cm ke points (1 cm = 28.3465 points)
    private static final float CM_TO_PT = 28.3465f;

    // Margin dan spacing
    private static final int MARGIN_PT = (int) (1.5 * CM_TO_PT); // 1.5cm margin
    private static final int SPACING_PT = (int) (0.5 * CM_TO_PT); // 0.5cm spacing

    // Threading components
    private ExecutorService pdfExecutor;
    private Handler mainHandler;
    private volatile boolean isGenerationCancelled = false;
    private ProgressDialog progressDialog;

    // ==================== COMPRESSION SETTINGS CLASS (STATIC) ====================
    public static class CompressionSettings {
        
        // Enum harus static karena berada di dalam static class
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

            // Static method di dalam enum - valid karena enum adalah static member
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
            if (level == CompressionLevel.NONE){
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

    // ==================== BITMAP COMPRESSOR ====================
    private class BitmapCompressor {
        private CompressionSettings settings;

        public BitmapCompressor(CompressionSettings settings) {
            this.settings = settings;
        }

        public Bitmap compressBitmap(Bitmap original, int targetWidth, int targetHeight) {
            if (settings.getLevel() == CompressionSettings.CompressionLevel.NONE) {
                return original;
            }

            Bitmap compressed = original;

            // 1. Downsampling
            compressed = applyDownsampling(compressed, targetWidth, targetHeight);

            // 2. Grayscale if needed
            if (settings.isConvertToGrayscale()) {
                compressed = convertToGrayscale(compressed);
            }

            // 3. Re-encode with JPEG compression
            compressed = reencodeBitmap(compressed);

            return compressed;
        }

        private Bitmap applyDownsampling(Bitmap bitmap, int targetWidth, int targetHeight) {
            int factor = settings.getDownsamplingFactor();
            if (factor <= 0) {
                return bitmap;
            }

            int newWidth = Math.max(targetWidth / factor, 100);
            int newHeight = Math.max(targetHeight / factor, 100);

            return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true);
        }

        private Bitmap convertToGrayscale(Bitmap original) {
            Bitmap grayscale = Bitmap.createBitmap(
                    original.getWidth(),
                    original.getHeight(),
                    Bitmap.Config.ARGB_8888
            );

            Canvas canvas = new Canvas(grayscale);
            Paint paint = new Paint();
            android.graphics.ColorMatrix colorMatrix = new android.graphics.ColorMatrix();
            colorMatrix.setSaturation(0);
            android.graphics.ColorMatrixColorFilter filter = new android.graphics.ColorMatrixColorFilter(colorMatrix);
            paint.setColorFilter(filter);
            canvas.drawBitmap(original, 0, 0, paint);

            if (original != grayscale && !original.isRecycled()) {
                original.recycle();
            }

            return grayscale;
        }

        private Bitmap reencodeBitmap(Bitmap bitmap) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, settings.getJpegQuality(), baos);

            byte[] compressedData = baos.toByteArray();
            Bitmap compressedBitmap = BitmapFactory.decodeByteArray(compressedData, 0, compressedData.length);

            try {
                baos.close();
            } catch (IOException e) {
                e.printStackTrace();
            }

            if (bitmap != compressedBitmap && !bitmap.isRecycled()) {
                bitmap.recycle();
            }

            return compressedBitmap;
        }

        public Bitmap decodeWithSampling(Uri uri, int reqWidth, int reqHeight) {
            try {
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inJustDecodeBounds = true;

                InputStream is = getContentResolver().openInputStream(uri);
                BitmapFactory.decodeStream(is, null, options);
                is.close();

                options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);
                options.inSampleSize *= settings.getDownsamplingFactor();

                options.inJustDecodeBounds = false;
                options.inPreferredConfig = settings.isConvertToGrayscale() ?
                        Bitmap.Config.RGB_565 : Bitmap.Config.ARGB_8888;
                options.inDither = true;

                is = getContentResolver().openInputStream(uri);
                Bitmap bitmap = BitmapFactory.decodeStream(is, null, options);
                is.close();

                return bitmap;

            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
        }

        private int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
            final int height = options.outHeight;
            final int width = options.outWidth;
            int inSampleSize = 1;

            if (height > reqHeight || width > reqWidth) {
                final int halfHeight = height / 2;
                final int halfWidth = width / 2;

                while ((halfHeight / inSampleSize) >= reqHeight &&
                        (halfWidth / inSampleSize) >= reqWidth) {
                    inSampleSize *= 2;
                }
            }

            return inSampleSize;
        }
    }

    // ==================== IMAGE INFO CLASS ====================
    private class ImageInfo {
        Uri uri;
        int originalWidth;
        int originalHeight;
        int targetWidth;
        int targetHeight;
        int pageNumber;
        int rowNumber;
        int x;
        int y;
        float scaleFactor;

        ImageInfo(Uri uri, int originalWidth, int originalHeight) {
            this.uri = uri;
            this.originalWidth = originalWidth;
            this.originalHeight = originalHeight;

            // Gunakan currentImageWidthCm yang bisa diatur user
            int imageWidthPt = (int) (currentImageWidthCm * CM_TO_PT);
            this.targetWidth = imageWidthPt;
            this.targetHeight = calculateTargetHeight(originalWidth, originalHeight, imageWidthPt);

            // Batasi tinggi maksimum 15cm
            int maxHeight = (int) (15 * CM_TO_PT);
            if (this.targetHeight > maxHeight) {
                this.targetHeight = maxHeight;
                this.targetWidth = (int) (this.targetHeight * ((float) originalWidth / originalHeight));
            }

            // Batasi agar tidak melebihi lebar halaman
            int maxPageWidth = A4_WIDTH_PT - (2 * MARGIN_PT);
            if (this.targetWidth > maxPageWidth) {
                this.targetWidth = maxPageWidth;
                this.targetHeight = (int) (this.targetWidth * ((float) originalHeight / originalWidth));
            }

            // Batasi agar tidak melebihi tinggi halaman
            int maxPageHeight = A4_HEIGHT_PT - (2 * MARGIN_PT);
            if (this.targetHeight > maxPageHeight) {
                this.targetHeight = maxPageHeight;
                this.targetWidth = (int) (this.targetHeight * ((float) originalWidth / originalHeight));
            }

            this.scaleFactor = (float) this.targetWidth / originalWidth;
        }

        private int calculateTargetHeight(int origWidth, int origHeight, int fixedWidth) {
            float ratio = (float) origHeight / (float) origWidth;
            return (int) (fixedWidth * ratio);
        }
    }

    // ==================== ROW INFO CLASS ====================
    private class RowInfo {
        int rowIndex;
        int maxHeight;
        int startX;
        int startY;
        List<ImageInfo> images;

        RowInfo(int rowIndex, int startX, int startY) {
            this.rowIndex = rowIndex;
            this.startX = startX;
            this.startY = startY;
            this.maxHeight = 0;
            this.images = new ArrayList<>();
        }

        void addImage(ImageInfo image) {
            images.add(image);
            if (image.targetHeight > maxHeight) {
                maxHeight = image.targetHeight;
            }
        }
    }

    // ==================== PAGE INFO CLASS ====================
    private class PageInfoData {
        int pageNumber;
        Map<Integer, RowInfo> rows;
        int currentY;
        int currentRow;

        PageInfoData(int pageNumber) {
            this.pageNumber = pageNumber;
            this.rows = new LinkedHashMap<>();
            this.currentY = MARGIN_PT;
            this.currentRow = 0;
        }

        void addRow(RowInfo row) {
            rows.put(row.rowIndex, row);
            currentRow = row.rowIndex;
        }

        int getNextRowY() {
            RowInfo currentRowInfo = rows.get(currentRow);
            if (currentRowInfo != null) {
                return currentRowInfo.startY + currentRowInfo.maxHeight + SPACING_PT;
            }
            return currentY;
        }
    }

    // ==================== PDF GENERATION TASK ====================
    private class PdfGenerationTask implements Runnable {
        private final List<Uri> imageUris;
        private final CompressionSettings compressionSettings;
        private final boolean showBorders;
        private final boolean showDimensions;
        private final boolean showRowInfo;
        private final boolean showResolution;
        private final boolean showScaleFactor;

        public PdfGenerationTask(List<Uri> uris, CompressionSettings settings,
                                 boolean borders, boolean dimensions, boolean rowInfo,
                                 boolean resolution, boolean scaleFactor) {
            this.imageUris = new ArrayList<>(uris);
            this.compressionSettings = settings;
            this.showBorders = borders;
            this.showDimensions = dimensions;
            this.showRowInfo = rowInfo;
            this.showResolution = resolution;
            this.showScaleFactor = scaleFactor;
        }

        @Override
        public void run() {
            try {
                final List<PageInfoData> pages = layoutImages();
                
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        updateProgress(10, "Menganalisis gambar & menata layout...");
                    }
                });

                if (isGenerationCancelled) {
                    handleCancellation();
                    return;
                }

                BitmapCompressor compressor = new BitmapCompressor(compressionSettings);
                Map<Uri, Bitmap> compressedBitmapCache = new LinkedHashMap<Uri, Bitmap>() {
                    @Override
                    protected boolean removeEldestEntry(Map.Entry<Uri, Bitmap> eldest) {
                        if (size() > 5) {
                            eldest.getValue().recycle();
                            return true;
                        }
                        return false;
                    }
                };

                PdfDocument pdfDocument = new PdfDocument();
                final int totalPages = pages.size();
                
                for (int pageIdx = 0; pageIdx < pages.size(); pageIdx++) {
                    final PageInfoData pageInfoData = pages.get(pageIdx);
                    final int currentPageNumber = pageIdx + 1;

                    if (isGenerationCancelled) {
                        cleanup(compressedBitmapCache, pdfDocument);
                        handleCancellation();
                        return;
                    }

                    final int progress = 10 + (currentPageNumber * 80 / totalPages);
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            updateProgress(progress,
                                    String.format(Locale.US, "Memproses halaman %d dari %d...", 
                                            currentPageNumber, totalPages));
                        }
                    });

                    PageInfo pageInfo = new PageInfo.Builder(A4_WIDTH_PT, A4_HEIGHT_PT, currentPageNumber).create();
                    Page page = pdfDocument.startPage(pageInfo);
                    Canvas canvas = page.getCanvas();

                    Paint marginPaint = new Paint();
                    marginPaint.setStyle(Paint.Style.STROKE);
                    marginPaint.setColor(Color.parseColor("#CCCCCC"));
                    marginPaint.setStrokeWidth(0.3f);
                    canvas.drawRect(MARGIN_PT, MARGIN_PT,
                            A4_WIDTH_PT - MARGIN_PT,
                            A4_HEIGHT_PT - MARGIN_PT, marginPaint);

                    for (RowInfo row : pageInfoData.rows.values()) {
                        for (ImageInfo image : row.images) {
                            Bitmap bitmap = compressedBitmapCache.get(image.uri);
                            if (bitmap == null || bitmap.isRecycled()) {
                                bitmap = compressor.decodeWithSampling(
                                        image.uri,
                                        image.targetWidth,
                                        image.targetHeight
                                );

                                if (bitmap != null) {
                                    bitmap = compressor.compressBitmap(
                                            bitmap,
                                            image.targetWidth,
                                            image.targetHeight
                                    );
                                    compressedBitmapCache.put(image.uri, bitmap);
                                }
                            }

                            if (bitmap != null && !bitmap.isRecycled()) {
                                drawScaledBitmap(canvas, bitmap,
                                        image.x, image.y,
                                        image.targetWidth, image.targetHeight);

                                renderVisualElements(canvas, row, image, bitmap, true);
                            }
                        }
                    }

                    drawPageHeader(canvas, currentPageNumber, totalPages);
                    drawPageFooter(canvas);

                    pdfDocument.finishPage(page);
                }

                if (isGenerationCancelled) {
                    cleanup(compressedBitmapCache, pdfDocument);
                    handleCancellation();
                    return;
                }

                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        updateProgress(95, "Menyimpan file PDF...");
                    }
                });

                final Uri savedUri = savePdfDocument(pdfDocument);
                cleanup(compressedBitmapCache, pdfDocument);

                int totalRows = 0;
                for (PageInfoData page : pages) {
                    totalRows += page.rows.size();
                }
                final int finalTotalPages = pages.size();
                final int finalTotalRows = totalRows;

                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        progressDialog.dismiss();
                        btnGenerate.setEnabled(true);
                        btnGenerate.setText("Buat PDF");

                        if (savedUri != null) {
                            String successMsg = String.format(Locale.US,
                                    "✅ PDF berhasil dibuat!\n" +
                                            "%d halaman, %d baris, %d gambar\n" +
                                            "Lebar gambar: %.1f cm\n" +
                                            "%s\n" +
                                            "💾 Tersimpan di Documents/ImageToPDF/",
                                    finalTotalPages, finalTotalRows, imageUris.size(),
                                    currentImageWidthCm,
                                    compressionSettings.getCompressionSummary()
                            );

                            Toast.makeText(convert.this, successMsg, Toast.LENGTH_LONG).show();

                            selectedImageUris.clear();
                            tvStatus.setText("Belum ada gambar dipilih");
                            btnGenerate.setEnabled(false);
                        } else {
                            Toast.makeText(convert.this,
                                    "❌ Gagal menyimpan PDF", Toast.LENGTH_SHORT).show();
                        }
                    }
                });

            }catch (Exception e) {
                final String errMsg = e.getMessage();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(getApplicationContext(),
                        "❌ Error: " + errMsg,
                        Toast.LENGTH_LONG).show();
                        }
                        });
                        }
        }

        private void cleanup(Map<Uri, Bitmap> cache, PdfDocument doc) {
            for (Bitmap bitmap : cache.values()) {
                if (bitmap != null && !bitmap.isRecycled()) {
                    bitmap.recycle();
                }
            }
            cache.clear();

            if (doc != null) {
                doc.close();
            }
        }

        private void handleCancellation() {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    progressDialog.dismiss();
                    btnGenerate.setEnabled(true);
                    btnGenerate.setText("Buat PDF");
                    Toast.makeText(convert.this,
                            "⚠️ Pembuatan PDF dibatalkan", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    // ==================== ACTIVITY LIFECYCLE ====================
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        pdfExecutor = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());
        isGenerationCancelled = false;

        currentCompressionSettings = new CompressionSettings();

        // Membuat ScrollView sebagai root layout
        ScrollView scrollView = new ScrollView(this);
        scrollView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        scrollView.setFillViewport(true);
        scrollView.setPadding(0, 0, 0, 0);

        // Membuat LinearLayout utama yang akan dibungkus ScrollView
        LinearLayout mainLayout = createMainLayout();
        
        // Menambahkan mainLayout ke dalam ScrollView
        scrollView.addView(mainLayout);
        
        // Set content view dengan ScrollView
        setContentView(scrollView);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (pdfExecutor != null) {
            pdfExecutor.shutdown();
        }
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
    }

    // ==================== UI LAYOUT CREATION ====================
    private LinearLayout createMainLayout() {
        LinearLayout mainLayout = new LinearLayout(this);
        mainLayout.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT)); // WRAP_CONTENT agar tinggi mengikuti konten
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setPadding(50, 50, 50, 50);

        TextView tvTitle = new TextView(this);
        tvTitle.setText("🚦Ayo Mulai");
        tvTitle.setTextSize(22f);
        tvTitle.setTextColor(Color.BLACK);
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        tvTitle.setPadding(0, 0, 0, 10);
        mainLayout.addView(tvTitle);

        TextView tvInfo = new TextView(this);
        tvInfo.setText("Pilih Gambar dan Mulai Generate 👇");
        /*tvInfo.setText("📄 Dokumen: A4 (21cm x 29.7cm)\n" +
                "Layout: Per baris dengan tinggi max per baris \n" );*/
        tvInfo.setTextSize(13f);
        tvInfo.setTextColor(Color.DKGRAY);
        tvInfo.setPadding(0, 0, 0, 20);
        mainLayout.addView(tvInfo);

        // ==================== PANEL PENGATURAN LEBAR GAMBAR ====================
        LinearLayout widthPanel = createImageWidthPanel();
        mainLayout.addView(widthPanel);
        // =======================================================================

        LinearLayout compressionPanel = createCompressionPanel();
        mainLayout.addView(compressionPanel);

        LinearLayout settingsPanel = createSettingsPanel();
        mainLayout.addView(settingsPanel);

        Button btnSelect = new Button(this);
        btnSelect.setText("Pilih Gambar");
        btnSelect.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        btnSelect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectImages();
            }
        });
        mainLayout.addView(btnSelect);

        tvStatus = new TextView(this);
        tvStatus.setText("Belum ada gambar dipilih");
        tvStatus.setTextSize(14f);
        tvStatus.setTextColor(Color.GRAY);
        tvStatus.setPadding(0, 20, 0, 20);
        mainLayout.addView(tvStatus);

        progressContainer = createProgressContainer();
        mainLayout.addView(progressContainer);

        btnGenerate = new Button(this);
        btnGenerate.setText("Buat PDF");
        btnGenerate.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        btnGenerate.setEnabled(false);
        btnGenerate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                generatePdf();
            }
        });
        mainLayout.addView(btnGenerate);

        return mainLayout;
    }

    // ==================== PANEL PENGATURAN LEBAR GAMBAR ====================
    private LinearLayout createImageWidthPanel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(20, 20, 20, 20);
        panel.setBackgroundColor(Color.parseColor("#E3F2FD")); // Warna biru muda
        panel.setElevation(4f);

        TextView tvTitle = new TextView(this);
        tvTitle.setText("PENGATURAN LEBAR GAMBAR");
        tvTitle.setTextSize(16f);
        tvTitle.setTextColor(Color.parseColor("#1565C0"));
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        tvTitle.setPadding(0, 0, 0, 15);
        panel.addView(tvTitle);

        // Label untuk menampilkan lebar saat ini
        tvImageWidthLabel = new TextView(this);
        tvImageWidthLabel.setText(String.format(Locale.US, "Lebar: %.1f cm", currentImageWidthCm));
        tvImageWidthLabel.setTextSize(18f);
        tvImageWidthLabel.setTextColor(Color.parseColor("#0D47A1"));
        tvImageWidthLabel.setTypeface(null, android.graphics.Typeface.BOLD);
        tvImageWidthLabel.setPadding(0, 0, 0, 10);
        panel.addView(tvImageWidthLabel);

        // SeekBar untuk mengatur lebar
        LinearLayout seekBarRow = new LinearLayout(this);
        seekBarRow.setOrientation(LinearLayout.HORIZONTAL);
        seekBarRow.setPadding(0, 5, 0, 5);

        TextView tvMinLabel = new TextView(this);
        tvMinLabel.setText(String.format(Locale.US, "%.1f cm", MIN_WIDTH_CM));
        tvMinLabel.setTextSize(12f);
        tvMinLabel.setTextColor(Color.DKGRAY);

        seekBarImageWidth = new SeekBar(this);
        seekBarImageWidth.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        seekBarImageWidth.setMax(100); // 0-100 scale
        
        // Konversi default 3cm ke progress (3cm dari 0.5-10 = range 9.5cm)
        // Progress = (current - min) / (max - min) * 100
        int defaultProgress = (int)(((DEFAULT_WIDTH_CM - MIN_WIDTH_CM) / (MAX_WIDTH_CM - MIN_WIDTH_CM)) * 100);
        seekBarImageWidth.setProgress(defaultProgress);

        TextView tvMaxLabel = new TextView(this);
        tvMaxLabel.setText(String.format(Locale.US, "%.1f cm", MAX_WIDTH_CM));
        tvMaxLabel.setTextSize(12f);
        tvMaxLabel.setTextColor(Color.DKGRAY);

        seekBarRow.addView(tvMinLabel);
        seekBarRow.addView(seekBarImageWidth);
        seekBarRow.addView(tvMaxLabel);
        panel.addView(seekBarRow);

        // TextView untuk nilai dalam points (informasi tambahan)
        tvImageWidthValue = new TextView(this);
        updateImageWidthDisplay();
        tvImageWidthValue.setTextSize(13f);
        tvImageWidthValue.setTextColor(Color.parseColor("#1565C0"));
        tvImageWidthValue.setPadding(0, 10, 0, 0);
        panel.addView(tvImageWidthValue);

        // Listener untuk SeekBar
        seekBarImageWidth.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    // Konversi progress (0-100) ke lebar dalam cm (MIN_WIDTH_CM - MAX_WIDTH_CM)
                    float widthCm = MIN_WIDTH_CM + (progress / 100f) * (MAX_WIDTH_CM - MIN_WIDTH_CM);
                    currentImageWidthCm = widthCm;
                    updateImageWidthDisplay();
                    updateEstimatedSize(); // Update estimasi ukuran karena lebar berubah
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // Informasi batasan
        TextView tvInfo = new TextView(this);
        tvInfo.setText("ℹ️ Tinggi gambar akan menyesuaikan rasio asli\n" +
                      "Maksimal 15cm per gambar, menyesuaikan halaman A4");
        tvInfo.setTextSize(12f);
        tvInfo.setTextColor(Color.DKGRAY);
        tvInfo.setPadding(0, 15, 0, 0);
        panel.addView(tvInfo);

        return panel;
    }

    private void updateImageWidthDisplay() {
        int widthPt = (int) (currentImageWidthCm * CM_TO_PT);
        tvImageWidthLabel.setText(String.format(Locale.US, "Lebar: %.1f cm", currentImageWidthCm));
        tvImageWidthValue.setText(String.format(Locale.US, "Dalam points: %d pt (1 cm = %.1f pt)", 
                widthPt, CM_TO_PT));
    }
    // =======================================================================

    private LinearLayout createCompressionPanel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(20, 20, 20, 20);
        panel.setBackgroundColor(Color.parseColor("#E8F5E9"));
        panel.setElevation(4f);

        TextView tvTitle = new TextView(this);
        tvTitle.setText("PENGATURAN KOMPRESI");
        tvTitle.setTextSize(16f);
        tvTitle.setTextColor(Color.parseColor("#2E7D32"));
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        tvTitle.setPadding(0, 0, 0, 15);
        panel.addView(tvTitle);

        tvCompressionLabel = new TextView(this);
        tvCompressionLabel.setText("🔵 Sedang - Seimbang (Rekomendasi)");
        tvCompressionLabel.setTextSize(15f);
        tvCompressionLabel.setTextColor(Color.parseColor("#1565C0"));
        tvCompressionLabel.setTypeface(null, android.graphics.Typeface.BOLD);
        tvCompressionLabel.setPadding(0, 0, 0, 10);
        panel.addView(tvCompressionLabel);

        LinearLayout seekBarRow = new LinearLayout(this);
        seekBarRow.setOrientation(LinearLayout.HORIZONTAL);
        seekBarRow.setPadding(0, 5, 0, 5);

        TextView tvMinLabel = new TextView(this);
        tvMinLabel.setText("Ringan");
        tvMinLabel.setTextSize(12f);
        tvMinLabel.setTextColor(Color.DKGRAY);

        seekBarCompression = new SeekBar(this);
        seekBarCompression.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        seekBarCompression.setMax(100);
        seekBarCompression.setProgress(50);

        TextView tvMaxLabel = new TextView(this);
        tvMaxLabel.setText("Maksimal");
        tvMaxLabel.setTextSize(12f);
        tvMaxLabel.setTextColor(Color.DKGRAY);

        seekBarRow.addView(tvMinLabel);
        seekBarRow.addView(seekBarCompression);
        seekBarRow.addView(tvMaxLabel);
        panel.addView(seekBarRow);

        tvCompressionValue = new TextView(this);
        tvCompressionValue.setText("Kualitas: 75% | Downsample: 2x");
        tvCompressionValue.setTextSize(13f);
        tvCompressionValue.setTextColor(Color.BLACK);
        tvCompressionValue.setPadding(0, 10, 0, 10);
        panel.addView(tvCompressionValue);

        tvEstimatedSize = new TextView(this);
        tvEstimatedSize.setText("Estimasi ukuran: ~50% dari asli");
        tvEstimatedSize.setTextSize(13f);
        tvEstimatedSize.setTextColor(Color.parseColor("#2E7D32"));
        tvEstimatedSize.setTypeface(null, android.graphics.Typeface.ITALIC);
        tvEstimatedSize.setPadding(0, 5, 0, 15);
        panel.addView(tvEstimatedSize);

        View divider = new View(this);
        divider.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 1));
        divider.setBackgroundColor(Color.parseColor("#A5D6A7"));
        panel.addView(divider);

        LinearLayout checkboxContainer = new LinearLayout(this);
        checkboxContainer.setOrientation(LinearLayout.VERTICAL);
        checkboxContainer.setPadding(0, 15, 0, 0);

        chkGrayscale = new CheckBox(this);
        chkGrayscale.setText(" Konversi ke Grayscale (hemat 30% ukuran)");
        chkGrayscale.setTextSize(13f);
        chkGrayscale.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                currentCompressionSettings.setConvertToGrayscale(isChecked);
                updateEstimatedSize();
            }
        });

        chkRemoveMetadata = new CheckBox(this);
        chkRemoveMetadata.setText(" Hapus Metadata (hemat 5-10% ukuran)");
        chkRemoveMetadata.setTextSize(13f);
        chkRemoveMetadata.setChecked(true);
        chkRemoveMetadata.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                currentCompressionSettings.setRemoveMetadata(isChecked);
            }
        });

        checkboxContainer.addView(chkGrayscale);
        checkboxContainer.addView(chkRemoveMetadata);
        panel.addView(checkboxContainer);

        seekBarCompression.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    currentCompressionSettings.setSeekBarValue(progress);
                    updateCompressionUI();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        return panel;
    }

    private void updateCompressionUI() {
        CompressionSettings.CompressionLevel level = currentCompressionSettings.getLevel();

        String labelText = level.displayName;
        int labelColor;
        switch (level) {
            case NONE:
                labelColor = Color.parseColor("#757575");
                break;
            case LOW:
                labelColor = Color.parseColor("#2E7D32");
                break;
            case MEDIUM:
                labelColor = Color.parseColor("#1565C0");
                break;
            case HIGH:
                labelColor = Color.parseColor("#FF6F00");
                break;
            case MAXIMUM:
                labelColor = Color.parseColor("#C62828");
                break;
            default:
                labelColor = Color.BLACK;
        }
        tvCompressionLabel.setText(labelText);
        tvCompressionLabel.setTextColor(labelColor);

        tvCompressionValue.setText(String.format(Locale.US, "Kualitas: %d%% | Downsample: %dx",
                currentCompressionSettings.getJpegQuality(),
                currentCompressionSettings.getDownsamplingFactor()));

        updateEstimatedSize();
    }

    private void updateEstimatedSize() {
        if (selectedImageUris.isEmpty()) {
            tvEstimatedSize.setText("Pilih gambar untuk estimasi ukuran");
            return;
        }

        float compressionFactor = currentCompressionSettings.getLevel().sizeFactor;

        if (currentCompressionSettings.isConvertToGrayscale()) {
            compressionFactor *= 0.7f;
        }

        int percentReduction = (int) ((1 - compressionFactor) * 100);
        
        // Tambahkan informasi lebar gambar
        tvEstimatedSize.setText(String.format(Locale.US, 
                "Estimasi: Hemat %d%% ukuran | Lebar: %.1f cm",
                percentReduction, currentImageWidthCm));
    }

    private LinearLayout createSettingsPanel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(20, 20, 20, 20);
        panel.setBackgroundColor(Color.parseColor("#F5F5F5"));
        panel.setElevation(4f);

        TextView tvSettingsTitle = new TextView(this);
        tvSettingsTitle.setText("PENGATURAN VISUAL PDF");
        tvSettingsTitle.setTextSize(16f);
        tvSettingsTitle.setTextColor(Color.BLACK);
        tvSettingsTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        tvSettingsTitle.setPadding(0, 0, 0, 15);
        panel.addView(tvSettingsTitle);

        LinearLayout allLayout = createSwitchRow("Tampilkan Semua", true);
        switchShowAll = (Switch) allLayout.getChildAt(1);
        switchShowAll.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                switchShowBorders.setChecked(isChecked);
                switchShowDimensions.setChecked(isChecked);
                switchShowRowInfo.setChecked(isChecked);
                switchShowResolution.setChecked(isChecked);
                switchShowScaleFactor.setChecked(isChecked);
            }
        });
        panel.addView(allLayout);

        View divider = new View(this);
        divider.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 1));
        divider.setBackgroundColor(Color.parseColor("#CCCCCC"));
        panel.addView(divider);

        LinearLayout borderLayout = createSwitchRow("Tampilkan Border", true);
        switchShowBorders = (Switch) borderLayout.getChildAt(1);
        panel.addView(borderLayout);

        LinearLayout dimLayout = createSwitchRow("Tampilkan Ukuran (cm)", true);
        switchShowDimensions = (Switch) dimLayout.getChildAt(1);
        panel.addView(dimLayout);

        LinearLayout rowLayout = createSwitchRow("Tampilkan Info Baris", true);
        switchShowRowInfo = (Switch) rowLayout.getChildAt(1);
        panel.addView(rowLayout);

        LinearLayout resLayout = createSwitchRow("Tampilkan Resolusi (px)", false);
        switchShowResolution = (Switch) resLayout.getChildAt(1);
        panel.addView(resLayout);

        LinearLayout scaleLayout = createSwitchRow("Tampilkan Faktor Skala", false);
        switchShowScaleFactor = (Switch) scaleLayout.getChildAt(1);
        panel.addView(scaleLayout);

        return panel;
    }

    private LinearLayout createSwitchRow(String label, boolean defaultValue) {
        LinearLayout row = new LinearLayout(this);
        row.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, 10, 0, 10);

        TextView tvLabel = new TextView(this);
        tvLabel.setText(label);
        tvLabel.setTextSize(14f);
        tvLabel.setTextColor(Color.BLACK);
        tvLabel.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        Switch switchControl = new Switch(this);
        switchControl.setChecked(defaultValue);

        row.addView(tvLabel);
        row.addView(switchControl);

        return row;
    }

    private LinearLayout createProgressContainer() {
        LinearLayout container = new LinearLayout(this);
        container.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(10, 20, 10, 20);
        container.setVisibility(View.GONE);

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                50));
        progressBar.setMax(100);
        progressBar.setProgress(0);

        LinearLayout textContainer = new LinearLayout(this);
        textContainer.setOrientation(LinearLayout.HORIZONTAL);
        textContainer.setPadding(0, 10, 0, 0);

        TextView tvProgressLabel = new TextView(this);
        tvProgressLabel.setText("Memproses: ");
        tvProgressLabel.setTextSize(14f);
        tvProgressLabel.setTextColor(Color.BLACK);

        tvProgressPercent = new TextView(this);
        tvProgressPercent.setText("0%");
        tvProgressPercent.setTextSize(14f);
        tvProgressPercent.setTextColor(Color.parseColor("#2196F3"));
        tvProgressPercent.setTypeface(null, android.graphics.Typeface.BOLD);

        textContainer.addView(tvProgressLabel);
        textContainer.addView(tvProgressPercent);

        container.addView(progressBar);
        container.addView(textContainer);

        return container;
    }

    // ==================== IMAGE SELECTION ====================
    private void selectImages() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(Intent.createChooser(intent, "Pilih Gambar"), PICK_IMAGES_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PICK_IMAGES_CODE && resultCode == RESULT_OK && data != null) {
            selectedImageUris.clear();

            if (data.getClipData() != null) {
                int count = data.getClipData().getItemCount();
                for (int i = 0; i < count; i++) {
                    Uri imageUri = data.getClipData().getItemAt(i).getUri();
                    selectedImageUris.add(imageUri);
                }
            } else if (data.getData() != null) {
                Uri imageUri = data.getData();
                selectedImageUris.add(imageUri);
            }

            tvStatus.setText(String.format(Locale.US, "📸 %d gambar dipilih (lebar: %.1f cm)", 
                    selectedImageUris.size(), currentImageWidthCm));
            btnGenerate.setEnabled(selectedImageUris.size() > 0);
            updateEstimatedSize();
        }
    }

    // ==================== PDF GENERATION ====================
    private void generatePdf() {
        if (selectedImageUris.isEmpty()) {
            Toast.makeText(this, "Pilih gambar terlebih dahulu", Toast.LENGTH_SHORT).show();
            return;
        }

        showProgressDialog();

        btnGenerate.setEnabled(false);
        btnGenerate.setText("Memproses...");
        isGenerationCancelled = false;

        final boolean showBorders = switchShowBorders.isChecked();
        final boolean showDimensions = switchShowDimensions.isChecked();
        final boolean showRowInfo = switchShowRowInfo.isChecked();
        final boolean showResolution = switchShowResolution.isChecked();
        final boolean showScaleFactor = switchShowScaleFactor.isChecked();

        PdfGenerationTask task = new PdfGenerationTask(
                new ArrayList<>(selectedImageUris),
                currentCompressionSettings,
                showBorders, showDimensions, showRowInfo,
                showResolution, showScaleFactor
        );

        pdfExecutor.execute(task);
    }

    private void showProgressDialog() {
        progressDialog = new ProgressDialog(this);
        progressDialog.setTitle("📄 Membuat PDF");
        progressDialog.setMessage("Memulai proses...");
        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progressDialog.setMax(100);
        progressDialog.setProgress(0);
        progressDialog.setCancelable(true);
        progressDialog.setButton(DialogInterface.BUTTON_NEGATIVE, "Batal",
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        cancelPdfGeneration();
                    }
                });
        progressDialog.show();
    }

    private void updateProgress(int percent, String message) {
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.setProgress(percent);
            progressDialog.setMessage(message);
        }

        if (progressBar != null) {
            progressBar.setProgress(percent);
            tvProgressPercent.setText(percent + "%");
        }
    }

    private void cancelPdfGeneration() {
        isGenerationCancelled = true;
        Toast.makeText(this, "Membatalkan proses...", Toast.LENGTH_SHORT).show();
    }

    // ==================== LAYOUT ENGINE ====================
    private List<PageInfoData> layoutImages() {
        List<ImageInfo> allImageInfos = new ArrayList<>();
        List<PageInfoData> pages = new ArrayList<>();

        for (int i = 0; i < selectedImageUris.size(); i++) {
            Uri imageUri = selectedImageUris.get(i);
            ImageInfo imageInfo = getImageDimensions(imageUri);
            if (imageInfo != null) {
                allImageInfos.add(imageInfo);
            }
        }

        if (allImageInfos.isEmpty()) {
            return pages;
        }

        PageInfoData currentPage = new PageInfoData(1);
        pages.add(currentPage);

        int currentX = MARGIN_PT;
        int currentRow = 0;
        RowInfo currentRowInfo = new RowInfo(currentRow, currentX, currentPage.currentY);
        currentPage.addRow(currentRowInfo);

        for (ImageInfo image : allImageInfos) {
            if (currentX + image.targetWidth > A4_WIDTH_PT - MARGIN_PT) {
                currentRow++;
                int newY = currentPage.getNextRowY();

                if (newY + image.targetHeight > A4_HEIGHT_PT - MARGIN_PT) {
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

    private ImageInfo getImageDimensions(Uri uri) {
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;

            InputStream inputStream = getContentResolver().openInputStream(uri);
            BitmapFactory.decodeStream(inputStream, null, options);
            inputStream.close();

            return new ImageInfo(uri, options.outWidth, options.outHeight);

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // ==================== RENDERING HELPERS ====================
    private void drawScaledBitmap(Canvas canvas, Bitmap originalBitmap,
                                  float left, float top, float targetWidth, float targetHeight) {
        Matrix matrix = new Matrix();
        float scaleX = targetWidth / originalBitmap.getWidth();
        float scaleY = targetHeight / originalBitmap.getHeight();
        matrix.setScale(scaleX, scaleY);
        matrix.postTranslate(left, top);

        Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
        paint.setAntiAlias(true);
        paint.setFilterBitmap(true);
        paint.setDither(true);

        canvas.drawBitmap(originalBitmap, matrix, paint);
    }

    private void renderVisualElements(Canvas canvas, RowInfo row, ImageInfo image,
                                      Bitmap originalBitmap, boolean hasBitmap) {
        float textY = image.y + image.targetHeight + 12;

        if (switchShowBorders.isChecked() && hasBitmap) {
            Paint borderPaint = new Paint();
            borderPaint.setStyle(Paint.Style.STROKE);
            borderPaint.setColor(Color.LTGRAY);
            borderPaint.setStrokeWidth(0.5f);
            borderPaint.setAntiAlias(true);

            canvas.drawRect(image.x - 1, image.y - 1,
                    image.x + image.targetWidth + 1,
                    image.y + image.targetHeight + 1, borderPaint);
        }

        if (switchShowRowInfo.isChecked()) {
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

        if (switchShowDimensions.isChecked()) {
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

        if (switchShowResolution.isChecked()) {
            Paint resPaint = new Paint();
            resPaint.setColor(Color.parseColor("#2E7D32"));
            resPaint.setTextSize(7f);
            resPaint.setAntiAlias(true);

            String resInfo = String.format(Locale.US, "%dx%d px",
                    image.originalWidth, image.originalHeight);
            canvas.drawText(resInfo, image.x, textY, resPaint);
            textY += 12;
        }

        if (switchShowScaleFactor.isChecked()) {
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

    private void drawPageHeader(Canvas canvas, int currentPage, int totalPages) {
        Paint headerPaint = new Paint();
        headerPaint.setColor(Color.BLACK);
        headerPaint.setTextSize(12f);
        headerPaint.setFakeBoldText(true);
        headerPaint.setAntiAlias(true);

        String headerText = String.format(Locale.US, "Halaman %d dari %d", currentPage, totalPages);
        canvas.drawText(headerText, MARGIN_PT, MARGIN_PT - 15, headerPaint);
    }

    private void drawPageFooter(Canvas canvas) {
        Paint footerPaint = new Paint();
        footerPaint.setColor(Color.DKGRAY);
        footerPaint.setTextSize(8f);
        footerPaint.setAntiAlias(true);

        String dateText = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US)
                .format(new Date());
        String footerText = String.format(Locale.US, "Image to PDF Pro • Generated: %s • Lebar: %.1f cm • Kompresi: %s",
                dateText, currentImageWidthCm, currentCompressionSettings.getLevel().displayName);

        canvas.drawText(footerText, MARGIN_PT, A4_HEIGHT_PT - 20, footerPaint);
    }

    // ==================== SAVE PDF ====================
    private Uri savePdfDocument(PdfDocument pdfDocument) {
        try {
            ContentResolver resolver = getContentResolver();
            ContentValues values = new ContentValues();

            String fileName = "ImageToPDF_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
                    .format(new Date()) + ".pdf";
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
            values.put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf");

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.put(MediaStore.MediaColumns.RELATIVE_PATH,
                        Environment.DIRECTORY_DOCUMENTS + "/ImageToPDF");
            }

            Uri pdfUri = resolver.insert(MediaStore.Files.getContentUri("external"), values);

            if (pdfUri != null) {
                OutputStream outputStream = resolver.openOutputStream(pdfUri);
                pdfDocument.writeTo(outputStream);
                outputStream.close();
                return pdfUri;
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }
}