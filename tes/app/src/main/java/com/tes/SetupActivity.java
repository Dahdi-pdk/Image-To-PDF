package com.tes;

import androidx.appcompat.app.AppCompatActivity;

import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * SetupActivity - Activity untuk konfigurasi sebelum konversi
 * User bisa atur kompresi, ukuran gambar, dan visual elements
 */
public class SetupActivity extends AppCompatActivity {

    private static final int REQUEST_RESELECT_IMAGES = 200;

    // Selected images
    private ArrayList<Uri> selectedImageUris;

    // UI Components
    private TextView tvImagesCount;
    private Button btnReselectImages;
    private Button btnStartConversion;

    // Compression settings UI
    private SeekBar seekBarCompression;
    private TextView tvCompressionValue;
    private TextView tvEstimatedSize;
    private CheckBox chkGrayscale;
    private CheckBox chkRemoveMetadata;

    // Image size settings UI
    private SeekBar seekBarImageWidth;
    private TextView tvImageWidthValue;

    // Visual elements switches
    private Switch switchShowAll;
    private Switch switchShowBorders;
    private Switch switchShowDimensions;
    private Switch switchShowRow;
    private Switch switchShowResolution;
    private Switch switchShowScale;

    // Settings objects
    private CompressionSettings compressionSettings;
    private float imageWidthCm = 3.0f; // Default 3cm

    // Constants for image width
    private static final float MIN_WIDTH_CM = 0.5f;
    private static final float MAX_WIDTH_CM = 10.0f;
    private static final float DEFAULT_WIDTH_CM = 3.0f;

    // Threading
    private ExecutorService pdfExecutor;
    private Handler mainHandler;
    private ProgressDialog progressDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setup);

        // Get selected images from intent
        selectedImageUris = getIntent().getParcelableArrayListExtra("IMAGE_URIS");
        if (selectedImageUris == null || selectedImageUris.isEmpty()) {
            Toast.makeText(this, R.string.msg_no_images_selected, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Initialize settings
        compressionSettings = new CompressionSettings();
        pdfExecutor = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());

        // Initialize views
        initializeViews();
        setupListeners();
        updateUI();
    }

    /**
     * Initialize all view references
     */
    private void initializeViews() {
        // Images info
        tvImagesCount = findViewById(R.id.tv_images_count);
        btnReselectImages = findViewById(R.id.btn_reselect_images);
        btnStartConversion = findViewById(R.id.btn_start_conversion);

        // Compression settings
        seekBarCompression = findViewById(R.id.seekbar_compression);
        tvCompressionValue = findViewById(R.id.tv_compression_value);
        tvEstimatedSize = findViewById(R.id.tv_estimated_size);
        chkGrayscale = findViewById(R.id.chk_grayscale);
        chkRemoveMetadata = findViewById(R.id.chk_remove_metadata);

        // Image size settings
        seekBarImageWidth = findViewById(R.id.seekbar_image_width);
        tvImageWidthValue = findViewById(R.id.tv_image_width_value);

        // Visual elements
        switchShowAll = findViewById(R.id.switch_show_all);
        switchShowBorders = findViewById(R.id.switch_show_borders);
        switchShowDimensions = findViewById(R.id.switch_show_dimensions);
        switchShowRow = findViewById(R.id.switch_show_row);
        switchShowResolution = findViewById(R.id.switch_show_resolution);
        switchShowScale = findViewById(R.id.switch_show_scale);
    }

    /**
     * Setup all event listeners
     */
    private void setupListeners() {
        // Reselect images button
        btnReselectImages.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish(); // Kembali ke MainActivity
            }
        });

        // Start conversion button
        btnStartConversion.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startConversion();
            }
        });

        // Compression seekbar
        seekBarCompression.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                compressionSettings.setSeekBarValue(progress);
                updateCompressionUI();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // Image width seekbar
        seekBarImageWidth.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                // Convert progress (0-95) to width (0.5-10 cm)
                imageWidthCm = MIN_WIDTH_CM + (progress / 95.0f) * (MAX_WIDTH_CM - MIN_WIDTH_CM);
                updateImageWidthUI();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // Grayscale checkbox
        chkGrayscale.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                compressionSettings.setConvertToGrayscale(isChecked);
                updateEstimatedSize();
            }
        });

        // Remove metadata checkbox
        chkRemoveMetadata.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                compressionSettings.setRemoveMetadata(isChecked);
            }
        });

        // Show all switch
        switchShowAll.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                switchShowBorders.setChecked(isChecked);
                switchShowDimensions.setChecked(isChecked);
                switchShowRow.setChecked(isChecked);
                switchShowResolution.setChecked(isChecked);
                switchShowScale.setChecked(isChecked);
            }
        });
    }

    /**
     * Update all UI elements
     */
    private void updateUI() {
        // Images count
        tvImagesCount.setText(String.format(Locale.US, 
                getString(R.string.setup_images_selected), selectedImageUris.size()));

        // Compression settings
        updateCompressionUI();

        // Image width
        updateImageWidthUI();

        // Checkboxes
        chkRemoveMetadata.setChecked(compressionSettings.isRemoveMetadata());
        chkGrayscale.setChecked(compressionSettings.isConvertToGrayscale());
    }

    /**
     * Update compression UI elements
     */
    private void updateCompressionUI() {
        CompressionSettings.CompressionLevel level = compressionSettings.getLevel();
        
        tvCompressionValue.setText(level.displayName);
        updateEstimatedSize();
    }

    /**
     * Update estimated file size
     */
    private void updateEstimatedSize() {
        // Perkiraan kasar ukuran total
        long estimatedTotalSize = 0;
        
        BitmapCompressor compressor = new BitmapCompressor(compressionSettings);
        
        // Asumsi rata-rata ukuran gambar 1920x1080 untuk estimasi
        long avgEstimate = compressor.estimateCompressedSize(1920, 1080);
        estimatedTotalSize = avgEstimate * selectedImageUris.size();

        String sizeText = String.format(Locale.US, 
                getString(R.string.setup_estimated_size),
                BitmapCompressor.formatFileSize(estimatedTotalSize));
        
        tvEstimatedSize.setText(sizeText);
    }

    /**
     * Update image width UI
     */
    private void updateImageWidthUI() {
        tvImageWidthValue.setText(String.format(Locale.US, 
                getString(R.string.setup_width_value), imageWidthCm));
    }

    /**
     * Start PDF conversion process
     */
    private void startConversion() {
        // Show progress dialog
        progressDialog = new ProgressDialog(this);
        progressDialog.setTitle(R.string.progress_title);
        progressDialog.setMessage(getString(R.string.progress_message));
        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progressDialog.setMax(100);
        progressDialog.setCancelable(true);
        progressDialog.setButton(ProgressDialog.BUTTON_NEGATIVE, 
                getString(R.string.progress_cancel),
                new android.content.DialogInterface.OnClickListener() {
            @Override
            public void onClick(android.content.DialogInterface dialog, int which) {
                cancelConversion();
            }
        });
        progressDialog.show();

        // Disable UI
        disableUI();

        // Start conversion in background thread
        pdfExecutor.execute(new Runnable() {
            @Override
            public void run() {
                performConversion();
            }
        });
    }

    /**
     * Perform actual PDF conversion
     */
    private void performConversion() {
        try {
            // Create PDF generator
            PdfGenerator pdfGenerator = new PdfGenerator(
                    SetupActivity.this,
                    compressionSettings,
                    imageWidthCm
            );

            // Set visual elements flags
            pdfGenerator.setShowBorders(switchShowBorders.isChecked());
            pdfGenerator.setShowDimensions(switchShowDimensions.isChecked());
            pdfGenerator.setShowRowInfo(switchShowRow.isChecked());
            pdfGenerator.setShowResolution(switchShowResolution.isChecked());
            pdfGenerator.setShowScaleFactor(switchShowScale.isChecked());

            // Generate PDF with progress callback
            final PdfDocument[] pdfDocumentHolder = new PdfDocument[1];
            
            pdfDocumentHolder[0] = pdfGenerator.generatePdf(
                    selectedImageUris,
                    new PdfGenerator.ProgressCallback() {
                        @Override
                        public void onProgress(final int current, final int total, final String message) {
                            mainHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    if (progressDialog != null && progressDialog.isShowing()) {
                                        int percentage = (int) ((current / (float) total) * 100);
                                        progressDialog.setProgress(percentage);
                                        progressDialog.setMessage(message);
                                    }
                                }
                            });
                        }

                        @Override
                        public boolean isCancelled() {
                            return progressDialog == null || !progressDialog.isShowing();
                        }
                    }
            );

            // Check if cancelled
            if (pdfDocumentHolder[0] == null) {
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(SetupActivity.this, 
                                R.string.msg_conversion_cancelled, 
                                Toast.LENGTH_SHORT).show();
                        dismissProgressDialog();
                        enableUI();
                    }
                });
                return;
            }

            // Save PDF
            final FileManager.SaveResult saveResult = FileManager.savePdfDocument(
                    SetupActivity.this,
                    pdfDocumentHolder[0]
            );

            // Close PDF document
            pdfDocumentHolder[0].close();

            // Check save result
            if (saveResult != null) {
                // Success - open FinishActivity
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        dismissProgressDialog();
                        openFinishActivity(saveResult);
                    }
                });
            } else {
                // Failed
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        dismissProgressDialog();
                        Toast.makeText(SetupActivity.this,
                                String.format(getString(R.string.msg_conversion_failed), 
                                        "Gagal menyimpan file"),
                                Toast.LENGTH_LONG).show();
                        enableUI();
                    }
                });
            }

        } catch (final Exception e) {
            e.printStackTrace();
            
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    dismissProgressDialog();
                    Toast.makeText(SetupActivity.this,
                            String.format(getString(R.string.msg_conversion_failed), 
                                    e.getMessage()),
                            Toast.LENGTH_LONG).show();
                    enableUI();
                }
            });
        }
    }

    /**
     * Cancel conversion
     */
    private void cancelConversion() {
        dismissProgressDialog();
        enableUI();
    }

    /**
     * Dismiss progress dialog safely
     */
    private void dismissProgressDialog() {
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
    }

    /**
     * Disable UI during conversion
     */
    private void disableUI() {
        btnStartConversion.setEnabled(false);
        btnReselectImages.setEnabled(false);
        seekBarCompression.setEnabled(false);
        seekBarImageWidth.setEnabled(false);
        chkGrayscale.setEnabled(false);
        chkRemoveMetadata.setEnabled(false);
        switchShowAll.setEnabled(false);
        switchShowBorders.setEnabled(false);
        switchShowDimensions.setEnabled(false);
        switchShowRow.setEnabled(false);
        switchShowResolution.setEnabled(false);
        switchShowScale.setEnabled(false);
    }

    /**
     * Enable UI after conversion
     */
    private void enableUI() {
        btnStartConversion.setEnabled(true);
        btnReselectImages.setEnabled(true);
        seekBarCompression.setEnabled(true);
        seekBarImageWidth.setEnabled(true);
        chkGrayscale.setEnabled(true);
        chkRemoveMetadata.setEnabled(true);
        switchShowAll.setEnabled(true);
        switchShowBorders.setEnabled(true);
        switchShowDimensions.setEnabled(true);
        switchShowRow.setEnabled(true);
        switchShowResolution.setEnabled(true);
        switchShowScale.setEnabled(true);
    }

    /**
     * Open FinishActivity with save result
     */
    private void openFinishActivity(FileManager.SaveResult saveResult) {
        Intent intent = new Intent(this, FinishActivity.class);
        intent.putExtra("PDF_URI", saveResult.uri.toString());
        intent.putExtra("FILE_NAME", saveResult.fileName);
        intent.putExtra("DISPLAY_PATH", saveResult.displayPath);
        intent.putExtra("FILE_SIZE", saveResult.fileSize);
        intent.putExtra("PAGE_COUNT", saveResult.pageCount);
        startActivity(intent);
        finish(); // Close SetupActivity
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        // Cleanup
        dismissProgressDialog();
        
        if (pdfExecutor != null && !pdfExecutor.isShutdown()) {
            pdfExecutor.shutdownNow();
        }
    }
}
