package com.example.sc2079_group25;

import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class TestActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        TextView textView = new TextView(this);
        textView.setText("Hello! TestActivity is running 🚀");
        textView.setTextSize(24);

        setContentView(textView);
    }
}
