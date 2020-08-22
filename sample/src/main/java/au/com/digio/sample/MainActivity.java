package au.com.digio.sample;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;

import au.com.digio.glidecropper.widget.CroppedImageView;

public class MainActivity extends AppCompatActivity {

    public static final int PICKFILE_REQUEST_CODE = 1;

    private Uri imageUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    public void selectPhoto(View view) {
        Intent intent = new Intent();
        intent.setType("image/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);
        startActivityForResult(Intent.createChooser(intent, "Select Picture"), PICKFILE_REQUEST_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICKFILE_REQUEST_CODE && resultCode == RESULT_OK && data != null) {

            imageUri = data.getData();
            ((CroppedImageView) findViewById(R.id.productImage)).setImageURI(imageUri);
        }
    }

    public void clearCache(View view) {
        Glide.get(MainActivity.this).clearMemory();
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                Glide.get(MainActivity.this).clearDiskCache(); // Do in background
            }
        };
        new Thread(runnable).start();
    }
}