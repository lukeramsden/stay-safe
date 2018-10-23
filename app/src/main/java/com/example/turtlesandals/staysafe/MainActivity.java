package com.example.turtlesandals.staysafe;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.support.v4.app.NotificationCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.RemoteViews;
import com.example.turtlesandals.staysafe.GPSService;

public class MainActivity extends AppCompatActivity {


    private static final String NOTIFICATION_ID = "notification_id";

    private Button startButton;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        startButton = findViewById(R.id.startButton);
        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {


                if (startButton.getText().equals(getResources().getString(R.string.button_stop))) {
                    stopService();
                    startButton.setText(R.string.button_start);
                }
                else if (startButton.getText().equals(getResources().getString(R.string.button_start))) {
                    startService();
                    startButton.setText(R.string.button_stop);
                }
            }
        });
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopService();
    }


    public void startService() {
        Intent gpsServiceIntent = new Intent(this, GPSService.class);
        //gpsServiceIntent.putExtra(NOTIFICATION_ID, notification_id);

        startService(gpsServiceIntent);
    }

    public void stopService() {
        Intent gpsServiceIntent = new Intent(this, GPSService.class);
        stopService(gpsServiceIntent);
    }
}
