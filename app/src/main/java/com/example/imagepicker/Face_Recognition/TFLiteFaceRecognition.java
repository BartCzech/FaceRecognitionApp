package com.example.imagepicker.Face_Recognition;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.os.Build;
import android.util.Pair;

import com.example.imagepicker.DB.DBHelper;

import org.tensorflow.lite.Interpreter;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TFLiteFaceRecognition
        implements FaceClassifier {

    // This variable will determine how much (%) the face on the input image is similar to a registered face
    private static final float MAX_DISTANCE_THRESHOLD = 1.4f;

    //private static final int OUTPUT_SIZE = 512;
    private static final int OUTPUT_SIZE = 512;

    // Float model
    private static final float IMAGE_MEAN = 128.0f;
    private static final float IMAGE_STD = 128.0f;

    private boolean isModelQuantized;
    // Config values.
    private int inputSize;

    private int[] intValues;

    private float[][] embeedings;

    private ByteBuffer imgData;

    private Interpreter tfLite;

    public HashMap<String, Recognition> registered = new HashMap<>();
    DBHelper dbHelper;

    public void register(String name, Recognition rec) {
        dbHelper.insertFace(name,rec.getEmbeeding());
        registered.put(name, rec);
    }

    private TFLiteFaceRecognition(Context ctx) {
        dbHelper = new DBHelper(ctx);
        registered = dbHelper.getAllFaces();
    }

    //TODO loads the models into mapped byte buffer format
    private static MappedByteBuffer loadModelFile(AssetManager assets, String modelFilename)
            throws IOException {
        AssetFileDescriptor fileDescriptor = assets.openFd(modelFilename);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }



    public static FaceClassifier create(
            final AssetManager assetManager,
            final String modelFilename,
            final int inputSize,
            final boolean isQuantized,Context ctx)
            throws IOException {

        final TFLiteFaceRecognition d = new TFLiteFaceRecognition(ctx);
        d.inputSize = inputSize;

        try {
            d.tfLite = new Interpreter(loadModelFile(assetManager, modelFilename));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        d.isModelQuantized = isQuantized;
        // Pre-allocate buffers.
        int numBytesPerChannel;
        if (isQuantized) {
            numBytesPerChannel = 1; // Quantized
        } else {
            numBytesPerChannel = 4; // Floating point
        }
        d.imgData = ByteBuffer.allocateDirect(1 * d.inputSize * d.inputSize * 3 * numBytesPerChannel);
        d.imgData.order(ByteOrder.nativeOrder());
        d.intValues = new int[d.inputSize * d.inputSize];
        return d;
    }

    //TODO  looks for the nearest embeeding in the dataset
    // and retrurns the pair <id, distance>
    private Pair<String, Float> findNearest(float[] emb) {
        List<Pair<String, Float>> nearestList = new ArrayList<>();

        for (Map.Entry<String, Recognition> entry : registered.entrySet()) {
            final String name = entry.getKey();
            final float[] knownEmb = ((float[][]) entry.getValue().getEmbeeding())[0];

            float distance = 0;
            for (int i = 0; i < emb.length; i++) {
                float diff = emb[i] - knownEmb[i];
                distance += diff*diff;
            }
            distance = (float) Math.sqrt(distance);
            nearestList.add(new Pair<>(name, distance));
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            nearestList.sort(Comparator.comparing(pair -> pair.second));
        }

        // Log the top 3 nearest faces
        for (int i = 0; i < Math.min(3, nearestList.size()); i++) {
            Pair<String, Float> match = nearestList.get(i);
            float confidence = calculateConfidence(match.second);
            System.out.println("Top " + (i + 1) + ": Name = " + match.first + ", Distance = " + match.second +
                    ", Confidence = " + confidence + "%");
        }

        // Return the closest face
        return nearestList.isEmpty() ? null : nearestList.get(0);
    }

    private List<Pair<String, Float>> findThreeClosest(float[] emb) {
        List<Pair<String, Float>> nearestList = new ArrayList<>();

        for (Map.Entry<String, Recognition> entry : registered.entrySet()) {
            final String name = entry.getKey();
            final float[] knownEmb = ((float[][]) entry.getValue().getEmbeeding())[0];

            float distance = 0;
            for (int i = 0; i < emb.length; i++) {
                float diff = emb[i] - knownEmb[i];
                distance += diff * diff;
            }
            distance = (float) Math.sqrt(distance);
            nearestList.add(new Pair<>(name, distance));
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            nearestList.sort(Comparator.comparing(pair -> pair.second));
        }

        // Log the top 3 nearest faces
        for (int i = 0; i < Math.min(3, nearestList.size()); i++) {
            Pair<String, Float> match = nearestList.get(i);
            float confidence = calculateConfidence(match.second);
            System.out.println("Top " + (i + 1) + ": Name = " + match.first + ", Distance = " + match.second +
                    ", Likeness score: " + confidence + "%");
        }

        // Return the top 3 nearest faces
        return nearestList.subList(0, Math.min(3, nearestList.size()));
    }


    // Method to calculate confidence based on the distance
    private float calculateConfidence(float distance) {
        if (distance > MAX_DISTANCE_THRESHOLD) {
            return 0.0f; // Beyond the threshold, it's considered an unlikely match
        }
        // Calculate confidence as a percentage, inversely proportional to the distance
        return (1 - (distance / MAX_DISTANCE_THRESHOLD)) * 100;
    }


    //TODO TAKE INPUT IMAGE AND RETURN RECOGNITIONS
    @Override
    public Recognition recognizeImage(final Bitmap bitmap, boolean storeExtra) {
        bitmap.getPixels(intValues, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());
        imgData.rewind();
        for (int i = 0; i < inputSize; ++i) {
            for (int j = 0; j < inputSize; ++j) {
                int pixelValue = intValues[i * inputSize + j];
                if (isModelQuantized) {
                    // Quantized model
                    imgData.put((byte) ((pixelValue >> 16) & 0xFF));
                    imgData.put((byte) ((pixelValue >> 8) & 0xFF));
                    imgData.put((byte) (pixelValue & 0xFF));
                } else { // Float model
                    imgData.putFloat((((pixelValue >> 16) & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
                    imgData.putFloat((((pixelValue >> 8) & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
                    imgData.putFloat(((pixelValue & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
                }
            }
        }
        Object[] inputArray = {imgData};
        // Here outputMap is changed to fit the Face Mask detector
        Map<Integer, Object> outputMap = new HashMap<>();

        embeedings = new float[1][OUTPUT_SIZE];
        outputMap.put(0, embeedings);

        // Run the inference call.
        tfLite.runForMultipleInputsOutputs(inputArray, outputMap);


        float distance = Float.MAX_VALUE;

        String id = "0";
        String label = "?";

        if (registered.size() > 0) {
            final Pair<String, Float> nearest = findNearest(embeedings[0]);
            if (nearest != null) {
                final String name = nearest.first;
                label = name;
                distance = nearest.second;
            }
        }
        Recognition rec = new Recognition(
                id,
                label,
                distance,
                new RectF());


        if (storeExtra) {
            rec.setEmbeeding(embeedings);
        }

        return rec;
    }

    @Override
    public List<Recognition> recognizeThreeFromImage(Bitmap bitmap, boolean storeExtra) {
        List<Recognition> recognitionList = new ArrayList<>();

        bitmap.getPixels(intValues, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());
        imgData.rewind();
        for (int i = 0; i < inputSize; ++i) {
            for (int j = 0; j < inputSize; ++j) {
                int pixelValue = intValues[i * inputSize + j];
                if (isModelQuantized) {
                    imgData.put((byte) ((pixelValue >> 16) & 0xFF));
                    imgData.put((byte) ((pixelValue >> 8) & 0xFF));
                    imgData.put((byte) (pixelValue & 0xFF));
                } else {
                    imgData.putFloat((((pixelValue >> 16) & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
                    imgData.putFloat((((pixelValue >> 8) & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
                    imgData.putFloat(((pixelValue & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
                }
            }
        }

        Object[] inputArray = {imgData};
        Map<Integer, Object> outputMap = new HashMap<>();
        embeedings = new float[1][OUTPUT_SIZE];
        outputMap.put(0, embeedings);

        tfLite.runForMultipleInputsOutputs(inputArray, outputMap);

        if (registered.size() > 0) {
            List<Pair<String, Float>> threeClosest = findThreeClosest(embeedings[0]);
            for (Pair<String, Float> pair : threeClosest) {
                Recognition recognition = new Recognition(
                        "0",  // Placeholder for ID
                        pair.first,  // Name
                        pair.second,  // Distance
                        new RectF()  // Default location
                );

                if (storeExtra) {
                    recognition.setEmbeeding(embeedings);
                }

                recognitionList.add(recognition);
            }
        }
        return recognitionList;
    }
}
