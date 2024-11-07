package com.example.imagepicker;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.Manifest;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.imagepicker.face_recognition.FaceClassifier;
import com.example.imagepicker.face_recognition.TFLiteFaceRecognition;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;
import com.google.mlkit.vision.common.InputImage;

import java.io.FileDescriptor;
import java.io.IOException;
import java.util.List;

public class RecognitionActivity extends AppCompatActivity {

    private static final int pic_id = 123;
    private static final int CAMERA_REQUEST_CODE = 100;
    private Uri cameraImageUri;

    // Face detector declaration
    // High-accuracy landmark detection and face classification
    FaceDetectorOptions highAccuracyOpts =
            new FaceDetectorOptions.Builder()
                    .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE) // for live camera results this will not be efficient as FPS will go down
                    .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL) // locations of facial features, disabled by LANDMARK_MODE_NONE
                    .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL) // detect facial expressions or not, can be disabled by CLASSIFICATION_MODE_NONE
                    .build();

    // Real-time contour detection
    //    FaceDetectorOptions realTimeOpts =
    //            new FaceDetectorOptions.Builder()
    //                    .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
    //                    .build();

    FaceDetector detector; // just declared

    // Face classifier declaration
    FaceClassifier faceClassifier;

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
                            performFaceDetection(input);
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

        // Initializing FaceDetector and FaceClassifier
        detector = FaceDetection.getClient(highAccuracyOpts);
        try {
            faceClassifier = TFLiteFaceRecognition.create(getAssets(), "facenet.tflite", 160, false);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
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
            performFaceDetection(input);
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
            return Bitmap.createBitmap(input, 0, 0, input.getWidth(), input.getHeight(), rotationMatrix, true);
        }

        return input;
    }

    // Perform face detection
    public void performFaceDetection(Bitmap bitmap){
        Bitmap mutableBmp = bitmap.copy(Bitmap.Config.ARGB_8888, true); // we need to create a mutable copy of this bitmap
        Canvas canvas = new Canvas(mutableBmp);
        InputImage image = InputImage.fromBitmap(bitmap, 0);
        Task<List<Face>> result =
                detector.process(image)
                        .addOnSuccessListener(
                                new OnSuccessListener<List<Face>>() {
                                    @Override
                                    public void onSuccess(List<Face> faces) {
                                        // Task completed successfully
                                        Log.d("tryFace", "faces len = " + faces.size());

                                        for (Face face : faces) {
                                            Rect bounds = face.getBoundingBox();

                                            Paint p = new Paint(); // styling the rectangle
                                            p.setColor(Color.RED);
                                            p.setStyle(Paint.Style.STROKE);
                                            p.setStrokeWidth(10);

                                            performFaceRecognition(bounds, bitmap);

                                            canvas.drawRect(bounds, p);

                                            // Below there is a lot of interesting code from ML Kit.
                                            // But we are not gonna need it for basic face recognition.

//                                            float rotY = face.getHeadEulerAngleY();  // Head is rotated to the right rotY degrees
//                                            float rotZ = face.getHeadEulerAngleZ();  // Head is tilted sideways rotZ degrees
//
//                                            // If landmark detection was enabled (mouth, ears, eyes, cheeks, and
//                                            // nose available):
//                                            FaceLandmark leftEar = face.getLandmark(FaceLandmark.LEFT_EAR);
//                                            if (leftEar != null) {
//                                                PointF leftEarPos = leftEar.getPosition();
//                                            }
//
//                                            // If contour detection was enabled:
//                                            List<PointF> leftEyeContour =
//                                                    face.getContour(FaceContour.LEFT_EYE).getPoints();
//                                            List<PointF> upperLipBottomContour =
//                                                    face.getContour(FaceContour.UPPER_LIP_BOTTOM).getPoints();
//
//                                            // If classification was enabled:
//                                            if (face.getSmilingProbability() != null) {
//                                                float smileProb = face.getSmilingProbability();
//                                            }
//                                            if (face.getRightEyeOpenProbability() != null) {
//                                                float rightEyeOpenProb = face.getRightEyeOpenProbability();
//                                            }
//
//                                            // If face tracking was enabled:
//                                            if (face.getTrackingId() != null) {
//                                                int id = face.getTrackingId();
//                                            }
                                        }
                                        // showing whole image with highlighted faces
                                        imageView.setImageBitmap(mutableBmp);
                                    }
                                })
                        .addOnFailureListener(
                                new OnFailureListener() {
                                    @Override
                                    public void onFailure(@NonNull Exception e) {
                                        // Task failed with an exception
                                        // ...
                                    }
                                });
    }

    // Perform face recognition
    public void performFaceRecognition(Rect bound, Bitmap input){
        // Basically we will crop the area of input restricted by bound.
        if (bound.top < 0) {
            bound.top = 0;
        }
        if (bound.left < 0) {
            bound.left = 0;
        }
        if (bound.right > input.getWidth()) {
            bound.right = input.getWidth() - 1;
        }
        if (bound.bottom > input.getHeight()) {
            bound.bottom = input.getHeight() - 1;
        }
        Bitmap croppedFace = Bitmap.createBitmap(input, bound.left, bound.top, bound.width(), bound.height());
        // showing only cropped faces
//            imageView.setImageBitmap(croppedFace);
        croppedFace = Bitmap.createScaledBitmap(croppedFace, 160, 160, false);
        FaceClassifier.Recognition recognition = faceClassifier.recognizeImage(croppedFace, false);
        Log.d("tryFR", recognition.getTitle() + " " + recognition.getDistance());
        Toast.makeText(RecognitionActivity.this, "Face of: " + recognition.getTitle() + ", dist: " + recognition.getDistance(), Toast.LENGTH_SHORT).show();


    }

}
