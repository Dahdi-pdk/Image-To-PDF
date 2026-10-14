package com.tes;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import java.util.ArrayList;

/**
 * MainActivity - Entry point aplikasi
 * Menampilkan tombol pilih gambar dan petunjuk penggunaan
 */
public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_PERMISSION = 100;
    private static final int REQUEST_PICK_IMAGES = 101;

    private Button btnSelectImages;
    private Button btnAbout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize views
        btnSelectImages = findViewById(R.id.btn_select_images);
        btnAbout = findViewById(R.id.btn_about);

        // Set click listeners
        btnSelectImages.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                checkPermissionAndPickImages();
            }
        });

        btnAbout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openAboutActivity();
            }
        });
    }

    /**
     * Check permission dan buka image picker
     */
    private void checkPermissionAndPickImages() {
        // Android 6.0+ memerlukan runtime permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) 
                    != PackageManager.PERMISSION_GRANTED) {
                // Request permission
                ActivityCompat.requestPermissions(
                        this,
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE,
                                Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        REQUEST_PERMISSION
                );
                return;
            }
        }

        // Permission already granted, open image picker
        openImagePicker();
    }

    /**
     * Buka image picker untuk memilih gambar
     */
    private void openImagePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        
        try {
            startActivityForResult(
                    Intent.createChooser(intent, "Pilih Gambar"),
                    REQUEST_PICK_IMAGES
            );
        } catch (Exception e) {
            Toast.makeText(this, "Tidak dapat membuka galeri", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Buka AboutActivity
     */
    private void openAboutActivity() {
        Intent intent = new Intent(this, AboutActivity.class);
        startActivity(intent);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (requestCode == REQUEST_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, open image picker
                openImagePicker();
            } else {
                // Permission denied
                Toast.makeText(this, 
                        R.string.msg_permission_required, 
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_PICK_IMAGES && resultCode == RESULT_OK) {
            if (data != null) {
                ArrayList<Uri> selectedImages = new ArrayList<Uri>();

                // Handle multiple images
                ClipData clipData = data.getClipData();
                if (clipData != null) {
                    // Multiple images selected
                    for (int i = 0; i < clipData.getItemCount(); i++) {
                        Uri imageUri = clipData.getItemAt(i).getUri();
                        selectedImages.add(imageUri);
                    }
                } else if (data.getData() != null) {
                    // Single image selected
                    selectedImages.add(data.getData());
                }

                if (!selectedImages.isEmpty()) {
                    // Open SetupActivity with selected images
                    openSetupActivity(selectedImages);
                } else {
                    Toast.makeText(this, 
                            R.string.msg_no_images_selected, 
                            Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    /**
     * Buka SetupActivity dengan gambar yang dipilih
     */
    private void openSetupActivity(ArrayList<Uri> imageUris) {
        Intent intent = new Intent(this, SetupActivity.class);
        intent.putParcelableArrayListExtra("IMAGE_URIS", imageUris);
        startActivity(intent);
    }
}
