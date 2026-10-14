package com.tes;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

/**
 * AboutActivity - Activity untuk menampilkan informasi tentang aplikasi
 * Berisi deskripsi, fitur, info developer, dan link media sosial
 */
public class AboutActivity extends AppCompatActivity {

    private Button btnEmail;
    private Button btnGithub;
    private Button btnInstagram;

    // Contact information - ganti dengan info sebenarnya
    private static final String DEVELOPER_EMAIL = "dahdipdk@gmail.com";
    private static final String GITHUB_URL = "https://thumbs.dreamstime.com/b/monkey-macaca-sit-aggression-tamarind-tree-forest-show-hand-symbol-middle-finger-doing-you-bad-expression-295623454.jpg?w=576";
    private static final String INSTAGRAM_URL = "https://www.instagram.com/dahdi_pdk/";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);

        // Initialize views
        initializeViews();
        setupListeners();
    }

    /**
     * Initialize all view references
     */
    private void initializeViews() {
        btnEmail = findViewById(R.id.btn_email);
        btnGithub = findViewById(R.id.btn_github);
        btnInstagram = findViewById(R.id.btn_instagram);
    }

    /**
     * Setup all event listeners
     */
    private void setupListeners() {
        // Email button
        btnEmail.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openEmail();
            }
        });

        // GitHub button
        btnGithub.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openUrl(GITHUB_URL);
            }
        });

        // Instagram button
        btnInstagram.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openUrl(INSTAGRAM_URL);
            }
        });
    }

    /**
     * Open email app with pre-filled email address
     */
    private void openEmail() {
        try {
            Intent emailIntent = new Intent(Intent.ACTION_SENDTO);
            emailIntent.setData(Uri.parse("mailto:")); // only email apps
            emailIntent.putExtra(Intent.EXTRA_EMAIL, new String[]{DEVELOPER_EMAIL});
            emailIntent.putExtra(Intent.EXTRA_SUBJECT, "Image to PDF - Feedback");
            
            startActivity(Intent.createChooser(emailIntent, "Kirim Email"));
            
        } catch (Exception e) {
            Toast.makeText(this, 
                    "Tidak dapat membuka aplikasi email", 
                    Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Open URL in browser
     */
    private void openUrl(String url) {
        try {
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(browserIntent);
            
        } catch (Exception e) {
            Toast.makeText(this, 
                    "Tidak dapat membuka browser", 
                    Toast.LENGTH_SHORT).show();
        }
    }
}
