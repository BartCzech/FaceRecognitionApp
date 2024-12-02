package com.example.imagepicker;
import com.example.imagepicker.Face_Recognition.FaceClassifier;

public class ClassifierSingleton {
    private static FaceClassifier faceClassifier;

    public static FaceClassifier getFaceClassifier() {
        return faceClassifier;
    }

    public static void setFaceClassifier(FaceClassifier classifier) {
        faceClassifier = classifier;
    }
}

