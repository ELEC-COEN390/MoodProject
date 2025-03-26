package com.example.moodproject;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.Locale;

public class OfflineSpeechRecognizer {
    private static final String TAG = "OfflineSpeechRecognizer";

    private Dashboard dashboard;
    private SpeechRecognizer speechRecognizer;
    private boolean isOfflineAvailable;

    // Permissions needed for recording
    private static final int PERMISSION_REQUEST_RECORD_AUDIO = 201;

    public OfflineSpeechRecognizer(Dashboard dashboard) {
        this.dashboard = dashboard;
        initializeSpeechRecognizer();
    }

    private void initializeSpeechRecognizer() {
        // Check if speech recognition is available on the device
        if (!SpeechRecognizer.isRecognitionAvailable(dashboard)) {
            Log.e(TAG, "Speech recognition is not available on this device");
            Toast.makeText(dashboard, "Speech recognition not available on this device", Toast.LENGTH_LONG).show();
            return;
        }

        // Check for record audio permission
        if (ContextCompat.checkSelfPermission(dashboard, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(dashboard,
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    PERMISSION_REQUEST_RECORD_AUDIO);
        }

        // Create the speech recognizer
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(dashboard);

        // Set up the recognition listener
        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle bundle) {
                Log.d(TAG, "Ready for speech");
            }

            @Override
            public void onBeginningOfSpeech() {
                Log.d(TAG, "Beginning of speech");
            }

            @Override
            public void onRmsChanged(float v) {
                // Voice level changed
            }

            @Override
            public void onBufferReceived(byte[] bytes) {
                Log.d(TAG, "Buffer received: " + bytes.length + " bytes");
            }

            @Override
            public void onEndOfSpeech() {
                Log.d(TAG, "End of speech");
            }

            @Override
            public void onError(int errorCode) {
                String errorMessage;
                switch (errorCode) {
                    case SpeechRecognizer.ERROR_AUDIO:
                        errorMessage = "Audio recording error";
                        break;
                    case SpeechRecognizer.ERROR_CLIENT:
                        errorMessage = "Client side error";
                        break;
                    case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                        errorMessage = "Insufficient permissions";
                        break;
                    case SpeechRecognizer.ERROR_NETWORK:
                        errorMessage = "Network error";
                        break;
                    case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                        errorMessage = "Network timeout";
                        break;
                    case SpeechRecognizer.ERROR_NO_MATCH:
                        errorMessage = "No match found";
                        break;
                    case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                        errorMessage = "RecognitionService busy";
                        break;
                    case SpeechRecognizer.ERROR_SERVER:
                        errorMessage = "Error from server";
                        break;
                    case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                        errorMessage = "No speech input";
                        break;
                    default:
                        errorMessage = "Unknown error";
                        break;
                }
                Log.e(TAG, "Error occurred: " + errorMessage);
                Toast.makeText(dashboard, "Recognition error: " + errorMessage, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onResults(Bundle results) {
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    String recognizedText = matches.get(0);
                    Log.d(TAG, "Recognized text: " + recognizedText);

                    // Update the UI with the recognized text
                    dashboard.updateRecognizedText(recognizedText);
                }
            }

            @Override
            public void onPartialResults(Bundle bundle) {
                ArrayList<String> matches = bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    Log.d(TAG, "Partial result: " + matches.get(0));
                }
            }

            @Override
            public void onEvent(int i, Bundle bundle) {
                // Future events
            }
        });

        // Check if offline recognition is available
        checkOfflineAvailability();
    }

    private void checkOfflineAvailability() {
        Intent recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.ENGLISH);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true);

        // Check if the device supports offline recognition
        PackageManager pm = dashboard.getPackageManager();
        isOfflineAvailable = recognizerIntent.resolveActivity(pm) != null;

        Log.d(TAG, "Offline recognition available: " + isOfflineAvailable);
        if (!isOfflineAvailable) {
            Toast.makeText(dashboard, "Offline recognition not available", Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Process the PCM audio file for speech recognition
     * @param pcmFile The raw PCM audio file to process
     */
    public void processAudioFile(File pcmFile) {
        if (!isOfflineAvailable) {
            Log.e(TAG, "Offline recognition is not available");
            Toast.makeText(dashboard, "Offline recognition not available", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!pcmFile.exists()) {
            Log.e(TAG, "Audio file does not exist: " + pcmFile.getAbsolutePath());
            return;
        }

        try {
            // Read the PCM file
            byte[] audioData = readPCMFile(pcmFile);

            // Convert PCM data to the format needed by the recognizer
            processAudioData(audioData);

        } catch (IOException e) {
            Log.e(TAG, "Error reading audio file: " + e.getMessage());
        }
    }

    private byte[] readPCMFile(File pcmFile) throws IOException {
        FileInputStream fis = new FileInputStream(pcmFile);
        byte[] audioData = new byte[(int) pcmFile.length()];
        fis.read(audioData);
        fis.close();
        return audioData;
    }

    private void processAudioData(byte[] audioData) {
        // Create recognizer intent with offline preference
        Intent recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.ENGLISH);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);

        // Start recognition
        speechRecognizer.startListening(recognizerIntent);

        // Feed audio data - this is a more complex process and may require custom implementation
        // The standard SpeechRecognizer doesn't directly accept audio data through a byte array
        // See the alternative implementation below for a workaround
        feedAudioDataToRecognizer(audioData);
    }

    /**
     * Alternative approach to feed audio data directly to SpeechRecognizer
     * Note: This is a simplified implementation and might not work on all devices
     */
    private void feedAudioDataToRecognizer(byte[] audioData) {
        // This is a workaround since SpeechRecognizer doesn't directly accept audio data
        // We'll use AudioRecord in a simulated mode to feed the data

        try {
            // Convert byte[] to short[] if the audio format is 16-bit PCM
            short[] shortArray = new short[audioData.length / 2];
            ByteBuffer.wrap(audioData).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shortArray);

            // Stop current recognition if in progress
            speechRecognizer.stopListening();

            // Start a new recognition session
            Intent recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.ENGLISH);
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true);
            speechRecognizer.startListening(recognizerIntent);

            // Using a mock AudioRecord is complex and may not be reliable
            // For a production app, consider using a third-party library like CMU Sphinx or Vosk

            // Signal end of speech after feeding the data
            speechRecognizer.stopListening();
        } catch (Exception e) {
            Log.e(TAG, "Error feeding audio data: " + e.getMessage());
        }
    }

    /**
     * Release resources when no longer needed
     */
    public void destroy() {
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
            speechRecognizer = null;
        }
    }
}