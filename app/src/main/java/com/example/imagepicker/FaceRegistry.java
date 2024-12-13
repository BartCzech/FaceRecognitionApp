package com.example.imagepicker;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Environment;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;

import com.example.imagepicker.Face_Recognition.FaceClassifier;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.IOException;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.channels.FileChannel;

public class FaceRegistry {
    public static void addDefaultFaces(Context context, FaceClassifier faceClassifier) {
        try {
            // Path to the root dataset folder in assets
            String rootPath = "cleaned_images_data";

            // Check if cache exists
            File cacheFile = new File(context.getFilesDir(), "face_cache.json");
            if (cacheFile.exists()) {
                Log.d("FaceRegistry", "Loading faces from cache...");
                loadFacesFromCache(cacheFile, faceClassifier);
                return;
            }

            // If no cache exists, process all images and cache them
            String[] subfolders = context.getAssets().list(rootPath);

            if (subfolders == null || subfolders.length == 0) {
                Log.d("FaceRegistry", "No subfolders found in assets.");
                return;
            }

            JSONArray cacheData = new JSONArray(); // To store processed data

            for (String subfolder : subfolders) {
                String celebrityPath = rootPath + "/" + subfolder;
                String[] imageFiles = context.getAssets().list(celebrityPath);

                if (imageFiles == null || imageFiles.length == 0) {
                    Log.d("FaceRegistry", "No images found in subfolder: " + subfolder);
                    continue;
                }

                for (String imageFile : imageFiles) {
                    if (imageFile.endsWith(".jpg")) {
                        String name = extractNameFromFile(imageFile);

                        // Load the image as a Bitmap
                        try (InputStream is = context.getAssets().open(celebrityPath + "/" + imageFile)) {
                            Bitmap bitmap = BitmapFactory.decodeStream(is);

                            // Generate embeddings
                            FaceClassifier.Recognition recognition = faceClassifier.recognizeImage(bitmap, true);
                            faceClassifier.register(name, recognition);

                            // Add to cache
                            JSONObject faceData = new JSONObject();
                            faceData.put("name", name);
                            faceData.put("embedding", new JSONArray(recognition.getEmbeeding()));
                            cacheData.put(faceData);

                            Log.d("FaceRegistry", "Registered face: " + name + " (Image: " + imageFile + ")");
                        }
                    }
                }
            }

            // Save cache to file and copy to Downloads
            saveCacheToFile(cacheFile, cacheData);
            copyCacheToDownloads(cacheFile);

        } catch (IOException | JSONException e) {
            Log.e("FaceRegistry", "Error loading default faces: " + e.getMessage());
        }
    }

    private static String extractNameFromFile(String imageFile) {
        return imageFile.substring(0, imageFile.lastIndexOf('.'))
                .replace("-", " ")
                .replaceAll("\\d.*", "");
    }

    private static void loadFacesFromCache(File cacheFile, FaceClassifier faceClassifier) {
        try (BufferedReader reader = new BufferedReader(new FileReader(cacheFile))) {
            StringBuilder jsonBuilder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                jsonBuilder.append(line);
            }

            JSONArray cacheData = new JSONArray(jsonBuilder.toString());

            for (int i = 0; i < cacheData.length(); i++) {
                JSONObject faceData = cacheData.getJSONObject(i);
                String name = faceData.getString("name");
                JSONArray embeddingArray = faceData.getJSONArray("embedding");

                float[] embedding = new float[embeddingArray.length()];
                for (int j = 0; j < embeddingArray.length(); j++) {
                    embedding[j] = (float) embeddingArray.getDouble(j);
                }

                FaceClassifier.Recognition recognition = new FaceClassifier.Recognition(name, embedding);
                faceClassifier.register(name, recognition);

                Log.d("FaceRegistry", "Loaded face from cache: " + name);
            }
        } catch (IOException | JSONException e) {
            Log.e("FaceRegistry", "Error loading faces from cache: " + e.getMessage());
        }
    }

    private static void saveCacheToFile(File cacheFile, JSONArray cacheData) {
        try (FileWriter writer = new FileWriter(cacheFile)) {
            writer.write(cacheData.toString());
            Log.d("FaceRegistry", "Face cache saved to: " + cacheFile.getAbsolutePath());
        } catch (IOException e) {
            Log.e("FaceRegistry", "Error saving face cache: " + e.getMessage());
        }
    }

    private static void copyCacheToDownloads(File cacheFile) {
        try {
            File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            File publicFile = new File(downloadsDir, "face_cache.json");

            try (FileChannel srcChannel = new FileInputStream(cacheFile).getChannel();
                 FileChannel dstChannel = new FileOutputStream(publicFile).getChannel()) {
                dstChannel.transferFrom(srcChannel, 0, srcChannel.size());
            }

            Log.i("FaceRegistry", "File copied to Downloads: " + publicFile.getAbsolutePath());
        } catch (Exception e) {
            Log.e("FaceRegistry", "Error copying file to Downloads: " + e.getMessage());
        }
    }
}
