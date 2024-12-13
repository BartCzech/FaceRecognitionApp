package com.example.imagepicker;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import java.io.IOException; // For handling IO exceptions
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.util.Log;    // For logging messages
import android.widget.ProgressBar;
import android.widget.Toast;

import com.example.imagepicker.Face_Recognition.TFLiteFaceRecognition;

import com.example.imagepicker.Face_Recognition.FaceClassifier;

public class MainActivity extends AppCompatActivity {

//    public static HashMap<String, FaceClassifier.Recognition> registered = new HashMap<>();

    // Choosing which model to use
    // facenet better but slower
    // Either (mobile_face_net.tflite, 112, 192) or (facenet.tflite, 160, 512)
    public static final String MODEL = "facenet.tflite";
    public static final int MODEL_INPUT_SIZE = 160;
    public static final int OUTPUT_SIZE = 512;


    FaceClassifier faceClassifier;

    Button registerBtn, recognizeBtn, liveBtn;

    ExecutorService executorService = Executors.newSingleThreadExecutor(); // Background thread executor

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        registerBtn = findViewById(R.id.buttonregister);
        recognizeBtn = findViewById(R.id.buttonrecognize);
        liveBtn = findViewById(R.id.buttonlive);

        ProgressBar progressBar = findViewById(R.id.progressBar);
        progressBar.setVisibility(View.VISIBLE);

        executorService.execute(() -> {

                    try {
                        // Initialize TFLiteFaceRecognition
                        faceClassifier = TFLiteFaceRecognition.create(
                                getAssets(),
                                MODEL,
                                MODEL_INPUT_SIZE,
                                false,
                                getApplicationContext()
                        );

                        ClassifierSingleton.setFaceClassifier(faceClassifier);

                        // Register default faces
                        FaceRegistry.addDefaultFaces(this, faceClassifier);

                        // Notify UI thread when the model is ready
                        runOnUiThread(() -> {
                            Log.i("MainActivity", "Model loaded successfully!");
                            enableButtons(); // Enable buttons after initialization
                        });
                        runOnUiThread(() -> {
                            Toast.makeText(MainActivity.this, "Model loaded!", Toast.LENGTH_LONG).show();
                            progressBar.setVisibility(View.GONE);
                            enableButtons();
                        });

                    } catch (IOException e) {
                        e.printStackTrace();
                        Log.e("MainActivity", "Error initializing TFLiteFaceRecognition: " + e.getMessage());
                        runOnUiThread(() -> {
                            Toast.makeText(MainActivity.this, "Model failed to load.", Toast.LENGTH_LONG).show();
                        });
                    }
                });

        // Initially disable buttons until the model is ready
        Toast.makeText(MainActivity.this, "Please wait for the model to load...", Toast.LENGTH_LONG).show();
        disableButtons();



        registerBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(MainActivity.this,RegisterActivity.class));
            }
        });

        recognizeBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(MainActivity.this,RecognitionActivity.class));
            }
        });

        liveBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(MainActivity.this, LiveActivity.class));
            }
        });
    }

    private void disableButtons() {
        registerBtn.setEnabled(false);
        recognizeBtn.setEnabled(false);
        liveBtn.setEnabled(false);
    }

    private void enableButtons() {
        registerBtn.setEnabled(true);
        recognizeBtn.setEnabled(true);
        liveBtn.setEnabled(true);
    }
}