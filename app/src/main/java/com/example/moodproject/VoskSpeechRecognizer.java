package com.example.moodproject;

import android.content.res.AssetManager;
import android.util.Log;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;
import org.vosk.Model;
import org.vosk.Recognizer;
import org.vosk.android.RecognitionListener;
import org.vosk.android.SpeechService;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class VoskSpeechRecognizer implements RecognitionListener {
    private static final String TAG = "VoskSpeechRecognizer";

    private final Dashboard dashboard;
    private Model model;
    private boolean modelReady = false;

    public VoskSpeechRecognizer(Dashboard dashboard) {
        this.dashboard = dashboard;
        initModel();
    }

    private void initModel() {
        // Initialize the model in a background thread
        new Thread(() -> {
            try {
                Log.d(TAG, "Starting model initialization");

                // Check if model exists in internal storage
                File modelDir = new File(dashboard.getFilesDir(), "vosk-model");
                boolean modelExists = modelDir.exists() && new File(modelDir, "am").exists();

                if (!modelExists) {
                    // Show a message about model download
                    dashboard.runOnUiThread(() -> {
                        Toast.makeText(dashboard,
                                "Speech model needs to be downloaded. This might take a few minutes.",
                                Toast.LENGTH_LONG).show();
                    });

                    // Create the directory if it doesn't exist
                    if (!modelDir.exists()) {
                        boolean created = modelDir.mkdirs();
                        Log.d(TAG, "Model directory created: " + created);
                    }

                    // Try to find and extract the model
                    try {
                        boolean modelExtracted = false;

                        // First check if model is bundled as an asset
                        try {
                            String[] assets = dashboard.getAssets().list("");
                            for (String asset : assets) {
                                Log.d(TAG, "Found asset: " + asset);
                                if (asset.startsWith("model-")) {
                                    copyAssetFolder(dashboard.getAssets(), asset, modelDir.getAbsolutePath());
                                    modelExtracted = true;
                                    break;
                                }
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error checking assets", e);
                        }

                        if (!modelExtracted) {
                            Log.e(TAG, "Could not find model in assets");
                            dashboard.runOnUiThread(() -> {
                                Toast.makeText(dashboard,
                                        "Speech model not found. Please check the app installation.",
                                        Toast.LENGTH_LONG).show();
                            });
                            return;
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error extracting model", e);
                        dashboard.runOnUiThread(() -> {
                            Toast.makeText(dashboard,
                                    "Error preparing speech model: " + e.getMessage(),
                                    Toast.LENGTH_LONG).show();
                        });
                        return;
                    }
                }

                // Create model from the directory
                try {
                    Model model = new Model(modelDir.getAbsolutePath());
                    this.model = model;
                    modelReady = true;

                    Log.d(TAG, "Model loaded successfully");
                    dashboard.runOnUiThread(() ->
                            Toast.makeText(dashboard, "Speech recognition model ready", Toast.LENGTH_SHORT).show());
                } catch (Exception e) {
                    Log.e(TAG, "Error loading model", e);
                    dashboard.runOnUiThread(() ->
                            Toast.makeText(dashboard,
                                    "Error loading speech model: " + e.getMessage(),
                                    Toast.LENGTH_LONG).show());
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed in model initialization process", e);
                dashboard.runOnUiThread(() ->
                        Toast.makeText(dashboard,
                                "Error in speech model initialization: " + e.getMessage(),
                                Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    // Helper method to copy assets
    private void copyAssetFolder(AssetManager assetManager, String fromAssetPath, String toPath) throws IOException {
        String[] files = assetManager.list(fromAssetPath);

        if (files.length == 0) {
            // It's a file, not a folder
            copyAsset(assetManager, fromAssetPath, toPath);
        } else {
            // It's a folder
            File dir = new File(toPath);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            for (String file : files) {
                copyAssetFolder(assetManager,
                        fromAssetPath + "/" + file,
                        toPath + "/" + file);
            }
        }
    }

    private void copyAsset(AssetManager assetManager, String fromAssetPath, String toPath) throws IOException {
        InputStream in = assetManager.open(fromAssetPath);
        File outFile = new File(toPath);

        if (!outFile.exists()) {
            boolean created = outFile.createNewFile();
            if (!created) {
                throw new IOException("Could not create file: " + toPath);
            }
        }

        FileOutputStream out = new FileOutputStream(outFile);

        byte[] buffer = new byte[4096];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }

        in.close();
        out.flush();
        out.close();
    }

    /**
     * Process a PCM audio file for speech recognition
     */
    public void processAudioFile(File pcmFile) {
        if (!modelReady) {
            Log.e(TAG, "Model is not ready yet");
            dashboard.runOnUiThread(() ->
                    Toast.makeText(dashboard, "Speech recognition model is not ready yet", Toast.LENGTH_SHORT).show());
            return;
        }

        if (!pcmFile.exists()) {
            Log.e(TAG, "Audio file does not exist: " + pcmFile.getAbsolutePath());
            return;
        }

        new Thread(() -> {
            try {
                Log.d(TAG, "Starting to process audio file: " + pcmFile.getAbsolutePath());

                // Create a recognizer with the model
                Recognizer recognizer = new Recognizer(model, 44100.0f);

                // Read the PCM file
                FileInputStream fis = new FileInputStream(pcmFile);
                byte[] buffer = new byte[4096];
                int nbytes;

                // Process the audio file in chunks
                while ((nbytes = fis.read(buffer)) >= 0) {
                    if (recognizer.acceptWaveForm(buffer, nbytes)) {
                        // We have a result
                        String result = recognizer.getResult();
                        processResult(result);
                    } else {
                        // Partial result
                        String partialResult = recognizer.getPartialResult();
                        Log.d(TAG, "Partial: " + partialResult);
                    }
                }

                // Get final result
                String finalResult = recognizer.getFinalResult();
                processResult(finalResult);

                // Close resources
                fis.close();
                recognizer.close();

            } catch (IOException e) {
                Log.e(TAG, "Error processing audio file: " + e.getMessage(), e);
                dashboard.runOnUiThread(() ->
                        Toast.makeText(dashboard, "Error processing audio: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    /**
     * Process raw PCM data for speech recognition
     */
    public void processAudioData(byte[] audioData) {
        if (!modelReady) {
            Log.e(TAG, "Model is not ready yet");
            dashboard.runOnUiThread(() ->
                    Toast.makeText(dashboard, "Speech recognition model is not ready yet", Toast.LENGTH_SHORT).show());
            return;
        }

        new Thread(() -> {
            try {
                Log.d(TAG, "Processing raw audio data, length: " + audioData.length);

                // Create a recognizer with the model
                Recognizer recognizer = new Recognizer(model, 44100.0f);

                // Process the audio data
                if (recognizer.acceptWaveForm(audioData, audioData.length)) {
                    String result = recognizer.getResult();
                    processResult(result);
                }

                // Get final result
                String finalResult = recognizer.getFinalResult();
                processResult(finalResult);

                // Close resources
                recognizer.close();

            } catch (Exception e) {
                Log.e(TAG, "Error processing audio data: " + e.getMessage(), e);
                dashboard.runOnUiThread(() ->
                        Toast.makeText(dashboard, "Error processing audio: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void processResult(String resultJson) {
        try {
            Log.d(TAG, "Processing result: " + resultJson);
            JSONObject jsonResult = new JSONObject(resultJson);
            if (jsonResult.has("text")) {
                String recognizedText = jsonResult.getString("text");
                if (!recognizedText.isEmpty()) {
                    Log.d(TAG, "Recognized text: " + recognizedText);
                    dashboard.runOnUiThread(() -> dashboard.updateRecognizedText(recognizedText));
                } else {
                    Log.d(TAG, "Recognized text was empty");
                }
            } else {
                Log.d(TAG, "Result has no 'text' field");
            }
        } catch (JSONException e) {
            Log.e(TAG, "Error parsing JSON result: " + e.getMessage(), e);
        }
    }

    /**
     * Release resources
     */
    public void destroy() {
        if (model != null) {
            model.close();
            model = null;
        }
    }

    // RecognitionListener implementation
    @Override
    public void onPartialResult(String hypothesis) {
        Log.d(TAG, "Partial result: " + hypothesis);
    }

    @Override
    public void onResult(String hypothesis) {
        processResult(hypothesis);
    }

    @Override
    public void onFinalResult(String hypothesis) {
        processResult(hypothesis);
    }

    @Override
    public void onError(Exception e) {
        Log.e(TAG, "Recognition error: " + e.getMessage(), e);
    }

    @Override
    public void onTimeout() {
        Log.d(TAG, "Recognition timeout");
    }
}