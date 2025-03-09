package com.example.moodproject;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;


public class PreferencesActivity extends AppCompatActivity {

    private CheckBox musicCheckBox;
    private CheckBox sportsCheckBox;
    private CheckBox foodCheckBox;
    private CheckBox healthCheckBox;
    private CheckBox artsCheckBox;
    private Button save_button;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_preferences);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        save_button = findViewById(R.id.saveButton);
        musicCheckBox = findViewById(R.id.checkBox_music);
        sportsCheckBox = findViewById(R.id.checkBox_sports);
        foodCheckBox = findViewById(R.id.checkBox_food);
        healthCheckBox = findViewById(R.id.checkBox_health);
        artsCheckBox = findViewById(R.id.checkBox_arts);


        // Set up click listeners for checkboxes
        musicCheckBox.setOnClickListener(v -> updatePreferences());
        sportsCheckBox.setOnClickListener(v -> updatePreferences());
        foodCheckBox.setOnClickListener(v -> updatePreferences());
        healthCheckBox.setOnClickListener(v -> updatePreferences());
        artsCheckBox.setOnClickListener(v -> updatePreferences());

        // Load saved preferences and update checkboxes
        loadPreferences();

        // Set up save button click listener
        save_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                savePreferences();
                Intent intent = new Intent(PreferencesActivity.this,Dashboard.class);
                startActivity(intent);
            }
        });



    }

    private void savePreferences() {
        // Save the preferences to Firebase
    }

    private void loadPreferences() {
        // Load saved preferences from Firebase
    }

    private void updatePreferences() {
        // Update the preferences based on the checkboxes' states

    }
}