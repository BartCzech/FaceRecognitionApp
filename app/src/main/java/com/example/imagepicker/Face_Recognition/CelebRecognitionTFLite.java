package com.example.imagepicker.Face_Recognition;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.util.Log;

import org.tensorflow.lite.Interpreter;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

public class CelebRecognitionTFLite {
    private final Interpreter interpreter;

    public CelebRecognitionTFLite(AssetManager assetManager, String modelPath) {
        try {
            interpreter = new Interpreter(loadModelFile(assetManager, modelPath));
        } catch (Exception e) {
            throw new RuntimeException("Error initializing TFLite model: " + e.getMessage());
        }
    }

    public float[] recognize(Bitmap inputBitmap) {
        // Preprocess the image (same as before)
        Bitmap scaledBitmap = Bitmap.createScaledBitmap(inputBitmap, 256, 256, false);
        ByteBuffer inputBuffer = preprocessImage(scaledBitmap);

        // Allocate output array
        float[][] output = new float[1][997]; // Output size is 997, one score per celebrity

        // Run the model
        interpreter.run(inputBuffer, output);

        return output[0]; // Return the output vector
    }

    private ByteBuffer preprocessImage(Bitmap bitmap) {
        ByteBuffer buffer = ByteBuffer.allocateDirect(1 * 256 * 256 * 3 * 4); // Float (4 bytes)
        buffer.order(ByteOrder.nativeOrder());

        for (int y = 0; y < 256; y++) {
            for (int x = 0; x < 256; x++) {
                int pixel = bitmap.getPixel(x, y);

                // Normalize pixel values to [0, 1] by dividing by 255
                buffer.putFloat(((pixel >> 16) & 0xFF) / 255.0f); // R
                buffer.putFloat(((pixel >> 8) & 0xFF) / 255.0f);  // G
                buffer.putFloat((pixel & 0xFF) / 255.0f);         // B
            }
        }

        return buffer;
    }

    private MappedByteBuffer loadModelFile(AssetManager assetManager, String modelPath) throws IOException {
        try (FileInputStream fis = new FileInputStream(assetManager.openFd(modelPath).getFileDescriptor());
             FileChannel fileChannel = fis.getChannel()) {
            long startOffset = assetManager.openFd(modelPath).getStartOffset();
            long declaredLength = assetManager.openFd(modelPath).getDeclaredLength();
            return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
        }
    }

    public void close() {
        interpreter.close();
    }
}
