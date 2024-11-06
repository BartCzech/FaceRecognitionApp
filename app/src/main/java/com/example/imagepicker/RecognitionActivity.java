package com.example.imagepicker;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.Manifest;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.io.FileDescriptor;
import java.io.IOException;

public class RecognitionActivity extends AppCompatActivity {

    private static final int pic_id = 123;
    private static final int CAMERA_REQUEST_CODE = 100;
    private Uri cameraImageUri;

    // code to get the image from gallery and display it
    ActivityResultLauncher<Intent> galleryActivityResultLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            new ActivityResultCallback<ActivityResult>() {
                @Override
                public void onActivityResult(ActivityResult result) {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri galleryImageUri  = result.getData().getData();

                        if (galleryImageUri  != null) {
                            Log.d("Gallery URI", String.valueOf(galleryImageUri ));
//                            imageView.setImageURI(galleryImageUri);
                            Bitmap input = uriToBitmap(galleryImageUri);
                            input = rotateBitmap(input, galleryImageUri);
                            imageView.setImageBitmap(input);
                        } else {
                            Log.d("Gallery URI", "Received null URI from gallery.");
                        }
                    } else {
                        Log.d("Gallery URI", "Failed to get image from gallery.");
                    }
                }
            });

    CardView galleryCard,cameraCard;
    ImageView imageView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recognition);

        imageView = findViewById(R.id.imageView2);
        galleryCard = findViewById(R.id.gallerycard);
        cameraCard = findViewById(R.id.cameracard);

        // ask for permission of camera upon the first launch of the app
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
//            if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_DENIED || checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED) {
//                String[] permission = {Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE};
//                requestPermissions(permission, 112);
//            }
//        }

        galleryCard.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent galleryIntent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                galleryActivityResultLauncher.launch(galleryIntent);
            }
        });

        cameraCard.setOnClickListener(v -> {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED) {
                openCamera();
            } else {
                // Request the CAMERA permission
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CAMERA_REQUEST_CODE);
            }
        });
    }

    private void openCamera() {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.TITLE, "New Picture");
        values.put(MediaStore.Images.Media.DESCRIPTION, "From the Camera");
        cameraImageUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);

        Intent camera_intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        camera_intent.putExtra(MediaStore.EXTRA_OUTPUT, cameraImageUri);
        Log.d("Camera URI", String.valueOf(cameraImageUri));
        startActivityForResult(camera_intent, pic_id);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openCamera();
            } else {
                // Permission denied - show a message to the user
                // Optional: Inform the user why the permission is needed
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == pic_id && resultCode == RESULT_OK) {
            Log.d("URI retrieved", String.valueOf(cameraImageUri));
            Bitmap input = uriToBitmap(cameraImageUri);
            input = rotateBitmap(input, cameraImageUri);
            imageView.setImageBitmap(input);
        }
    }

    //TODO takes URI of the image and returns bitmap

    private Bitmap uriToBitmap(Uri selectedFileUri) {
        try {
            // Open a ParcelFileDescriptor for the given URI
            ParcelFileDescriptor parcelFileDescriptor = getContentResolver().openFileDescriptor(selectedFileUri, "r");

            // Get the FileDescriptor from the ParcelFileDescriptor
            FileDescriptor fileDescriptor = parcelFileDescriptor.getFileDescriptor();

            // Decode the FileDescriptor into a Bitmap
            Bitmap image = BitmapFactory.decodeFileDescriptor(fileDescriptor);

            // Close the ParcelFileDescriptor
            parcelFileDescriptor.close();

            return image;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    @SuppressLint("Range")
    public Bitmap rotateBitmap(Bitmap input, Uri imageUri) {
        String[] orientationColumn = { MediaStore.Images.Media.ORIENTATION };
        Cursor cur = getContentResolver().query(imageUri, orientationColumn, null, null, null);

        int orientation = -1;
        if (cur != null && cur.moveToFirst()) {
            orientation = cur.getInt(cur.getColumnIndex(MediaStore.Images.Media.ORIENTATION));
            Log.d("tryOrientation", orientation + "");
        }

        if (cur != null) {
            cur.close();
        }

        if (orientation != -1) {
            Matrix rotationMatrix = new Matrix();
            rotationMatrix.setRotate(orientation);
            Bitmap rotatedBitmap = Bitmap.createBitmap(input, 0, 0, input.getWidth(), input.getHeight(), rotationMatrix, true);
            return rotatedBitmap;
        }

        return input;
    }
}
