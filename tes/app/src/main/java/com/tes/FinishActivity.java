package com.tes;

import androidx.appcompat.app.AppCompatActivity;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

/**
 * FinishActivity - Activity untuk menampilkan hasil konversi
 * Menampilkan info file dan opsi untuk membuka, share, atau buka folder
 */
public class FinishActivity extends AppCompatActivity {

    private Uri pdfUri;
    private String fileName;
    private String displayPath;
    private long fileSize;
    private int pageCount;

    // UI Components
    private TextView tvFilename;
    private TextView tvLocation;
    private TextView tvFilesize;
    private TextView tvPages;
    private Button btnOpenPdf;
    private Button btnSharePdf;
    private Button btnOpenFolder;
    private Button btnConvertMore;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_finish);

        // Get data from intent
        Intent intent = getIntent();
        String uriString = intent.getStringExtra("PDF_URI");
        fileName = intent.getStringExtra("FILE_NAME");
        displayPath = intent.getStringExtra("DISPLAY_PATH");
        fileSize = intent.getLongExtra("FILE_SIZE", 0);
        pageCount = intent.getIntExtra("PAGE_COUNT", 0);

        if (uriString != null) {
            pdfUri = Uri.parse(uriString);
        }

        // Check if we have valid data
        if (pdfUri == null || fileName == null) {
            Toast.makeText(this, "Error: Data tidak valid", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Initialize views
        initializeViews();
        setupListeners();
        displayFileInfo();
    }

    /**
     * Initialize all view references
     */
    private void initializeViews() {
        tvFilename = findViewById(R.id.tv_filename);
        tvLocation = findViewById(R.id.tv_location);
        tvFilesize = findViewById(R.id.tv_filesize);
        tvPages = findViewById(R.id.tv_pages);
        
        btnOpenPdf = findViewById(R.id.btn_open_pdf);
        btnSharePdf = findViewById(R.id.btn_share_pdf);
        btnOpenFolder = findViewById(R.id.btn_open_folder);
        btnConvertMore = findViewById(R.id.btn_convert_more);
    }

    /**
     * Setup all event listeners
     */
    private void setupListeners() {
        // Open PDF button
        btnOpenPdf.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openPdfFile();
            }
        });

        // Share PDF button
        btnSharePdf.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sharePdfFile();
            }
        });

        // Open folder button
        btnOpenFolder.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openFolder();
            }
        });

        // Convert more button
        btnConvertMore.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                convertMore();
            }
        });
    }

    /**
     * Display file information
     */
    private void displayFileInfo() {
        tvFilename.setText(fileName);
        tvLocation.setText(displayPath);
        tvFilesize.setText(FileManager.formatFileSize(fileSize));
        tvPages.setText(String.format(Locale.US, "%d", pageCount));
    }

    /**
     * Open PDF file with external app
     */
    private void openPdfFile() {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(pdfUri, "application/pdf");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
            
            startActivity(intent);
            
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.msg_no_pdf_viewer, Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Share PDF file via external apps
     */
    private void sharePdfFile() {
        try {
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("application/pdf");
            shareIntent.putExtra(Intent.EXTRA_STREAM, pdfUri);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            
            Intent chooser = Intent.createChooser(shareIntent, getString(R.string.msg_share_title));
            startActivity(chooser);
            
        } catch (Exception e) {
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Open folder where PDF is saved
     * Note: Opening specific folder is limited on Android
     */
    private void openFolder() {
        try {
            // Try to open file manager
            Intent intent = new Intent(Intent.ACTION_VIEW);
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ - open Documents folder
                Uri documentsUri = Uri.parse("content://com.android.externalstorage.documents/document/primary:Documents");
                intent.setDataAndType(documentsUri, "resource/folder");
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } else {
                // Older Android - try to open with file manager
                intent.setDataAndType(Uri.parse("file://" + displayPath), "*/*");
            }
            
            startActivity(intent);
            
        } catch (Exception e) {
            // Fallback: open file manager app
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setType("resource/folder");
                
                Intent chooser = Intent.createChooser(intent, "Buka File Manager");
                startActivity(chooser);
                
            } catch (Exception e2) {
                Toast.makeText(this, 
                        "File tersimpan di: " + displayPath, 
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    /**
     * Return to MainActivity to convert more images
     */
    private void convertMore() {
        // Clear back stack and return to MainActivity
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    /**
     * Handle back button - return to MainActivity
     */
    @Override
    public void onBackPressed() {
        convertMore();
    }
}
