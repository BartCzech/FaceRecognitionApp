package com.example.imagepicker;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import com.example.imagepicker.Face_Recognition.FaceClassifier;

import java.util.HashMap;

public class MainActivity extends AppCompatActivity {

//    public static HashMap<String, FaceClassifier.Recognition> registered = new HashMap<>();

    // Choosing which model to use
    // facenet better but slower
    // Either (mobile_face_net.tflite, 112, 192) or (facenet.tflite, 160, 512)
    public static final String MODEL = "facenet.tflite";
    public static final int MODEL_INPUT_SIZE = 160;
    public static final int OUTPUT_SIZE = 512;

    Button registerBtn, recognizeBtn, liveBtn;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        registerBtn = findViewById(R.id.buttonregister);
        recognizeBtn = findViewById(R.id.buttonrecognize);
        liveBtn = findViewById(R.id.buttonlive);

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
}