package com.example.turtlesandals.staysafe;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationManager;
import android.nfc.Tag;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.widget.RemoteViews;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.SocketException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.Calendar;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.HttpsURLConnection;

public class GPSService extends Service
{
    private static final String TAG = "StaySafeGPS";
    private static final String baseURL = "https://data.police.uk/api/crimes-street/all-crime?poly=";
    public static final int LOCATION_INTERVAL = 1000;
    public static final float LOCATION_DISTANCE = 10f;
    public static final int DEFAULT_RETRY_ATTEMPTS = 1;
    public static final int DEFAULT_RETRY_DELAY = 15;
    private final int retryAttempts = DEFAULT_RETRY_ATTEMPTS;
    private final int retryDelaySeconds = DEFAULT_RETRY_DELAY;
    private int retryAttemptsLeft = retryAttempts;

    private LocationManager mLocationManager = null;

    private static final String CHANNEL_ID = "StaySafeNoti";
    private NotificationCompat.Builder builder;
    private int notification_id;
    private RemoteViews customView;
    private RemoteViews customBigView;
    private Context context;

    private String dateValue;
    private int totalCrimes = 0;


    private class LocationListener implements android.location.LocationListener
    {
        Location mLastLocation;

        public LocationListener(String provider)
        {
            Log.e(TAG, "LocationListener " + provider);
            mLastLocation = new Location(provider);
        }

        @Override
        public void onLocationChanged(Location location)
        {
            Log.e(TAG, "onLocationChanged: " + location);
            mLastLocation.set(location);
            getCrimeNumber(location);
        }

        @Override
        public void onProviderDisabled(String provider)
        {
            Log.e(TAG, "onProviderDisabled: " + provider);
        }

        @Override
        public void onProviderEnabled(String provider)
        {
            Log.e(TAG, "onProviderEnabled: " + provider);
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras)
        {
            Log.e(TAG, "onStatusChanged: " + provider);
        }
    }

    LocationListener[] mLocationListeners = new LocationListener[] {
            new LocationListener(LocationManager.GPS_PROVIDER),
            new LocationListener(LocationManager.NETWORK_PROVIDER)
    };

    @Override
    public IBinder onBind(Intent arg0)
    {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {
        super.onStartCommand(intent, flags, startId);
        sendNotification();
        Log.e(TAG, "onStartCommand");
        return START_NOT_STICKY;
    }

    @Override
    public void onCreate()
    {
        Log.e(TAG, "onCreate");

        getDate();

        initializeLocationManager();
        try {
            mLocationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER, LOCATION_INTERVAL, LOCATION_DISTANCE,
                    mLocationListeners[1]);
            getCrimeNumber(mLocationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER));
        } catch (java.lang.SecurityException ex) {
            Log.i(TAG, "fail to request location update, ignore", ex);
        } catch (IllegalArgumentException ex) {
            Log.d(TAG, "network provider does not exist, " + ex.getMessage());
        }
        try {
            mLocationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER, LOCATION_INTERVAL, LOCATION_DISTANCE,
                    mLocationListeners[0]);
            getCrimeNumber(mLocationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER));
        } catch (java.lang.SecurityException ex) {
            Log.i(TAG, "fail to request location update, ignore", ex);
        } catch (IllegalArgumentException ex) {
            Log.d(TAG, "gps provider does not exist " + ex.getMessage());
        }

        context = this;
        customView = new RemoteViews(getPackageName(), R.layout.notif);
        customBigView = new RemoteViews(getPackageName(), R.layout.notif_big);
        notification_id = (int) System.currentTimeMillis();
    }

    @Override
    public void onDestroy()
    {
        Log.e(TAG, "onDestroy");
        super.onDestroy();
        if (mLocationManager != null) {
            for (int i = 0; i < mLocationListeners.length; i++) {
                try {
                    mLocationManager.removeUpdates(mLocationListeners[i]);
                } catch (Exception ex) {
                    Log.i(TAG, "fail to remove location listners, ignore", ex);
                }
            }
        }
    }

    private void initializeLocationManager() {
        Log.e(TAG, "initializeLocationManager");
        if (mLocationManager == null) {
            mLocationManager = (LocationManager) getApplicationContext().getSystemService(Context.LOCATION_SERVICE);
        }
    }

    private void createNotification() {
        Intent notification_intent = new Intent(context, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, notification_intent, PendingIntent.FLAG_UPDATE_CURRENT);

        createNotificationChannel();

        builder = new NotificationCompat.Builder(context, CHANNEL_ID);
        builder
                .setSmallIcon(R.drawable.ic_launcher_background)
                .setOngoing(true)
                .setCustomContentView(customView)
                .setCustomBigContentView(customBigView)
                .setChannelId(CHANNEL_ID)
                .setOnlyAlertOnce(true)
                .setContentIntent(pendingIntent);

        builder.setSmallIcon(R.drawable.ic_notif);
        builder.setColor(ContextCompat.getColor(context, R.color.colorWhite));
    }

    private void createNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            CharSequence name = "Your_channel";
            String description = "Your_channel_desc";
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);

            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    private int getRiskFactor() {
        int risk = 0;

        /**
         * > 1000  = 10
         * <= 1000 = 9
         * <= 750  = 8
         * <= 600  = 7
         * <= 500  = 6
         * <= 250  = 5
         * <= 200  = 4
         * <= 150  = 3
         * <= 50   = 1
         */

        if(totalCrimes > 0 ) { risk = 1; }
        if(totalCrimes > 25 ) { risk = 2; }
        if(totalCrimes > 50 ) { risk = 3; }
        if(totalCrimes > 100 ) { risk = 4; }
        if(totalCrimes > 125 ) { risk = 5; }
        if(totalCrimes > 250 ) { risk = 6; }
        if(totalCrimes > 300 ) { risk = 7; }
        if(totalCrimes > 375 ) { risk = 8; }
        if(totalCrimes > 450) { risk = 9; }
        if(totalCrimes > 500) { risk = 10; }

        return risk;
    }

    private void sendNotification() {
        Log.d(TAG, "Sending notification");

        int riskFactor = getRiskFactor();
        customView.setTextViewText(R.id.notification_subtitle, "Risk: " + riskFactor + "/10");

        int riskNameId;

        if(riskFactor <= 3) { riskNameId = R.string.low_risk; }
        else if(riskFactor <= 6) { riskNameId = R.string.moderate_risk; }
        else { riskNameId = R.string.high_risk; }

        customBigView.setTextViewText(R.id.notification_subtitle, getResources().getString(riskNameId));
        customBigView.setTextViewText(R.id.notification_risk, "Risk: " + riskFactor + "/10");
        customBigView.setTextViewText(R.id.notification_crimes_per_month, "Crimes Per Month: " + Integer.toString(totalCrimes));

        createNotification();
        startForeground(notification_id, builder.build());
    }

    private void getCrimeNumber(Location lastLocation) {
        double long1 = lastLocation.getLongitude() - 0.005;
        double long2 = lastLocation.getLongitude() + 0.005;

        double lat1 = lastLocation.getLatitude() - 0.01;
        double lat2 = lastLocation.getLatitude() + 0.01;


        String urlString = baseURL + String.valueOf(lat1) + "," + String.valueOf(long1) + ":"
                                                                                + String.valueOf(lat1) + "," + String.valueOf(long2) + ":"
                                                                                + String.valueOf(lat2) + "," + String.valueOf(long1) + ":"
                                                                                + String.valueOf(lat2) + "," + String.valueOf(long2) + dateValue;
        Log.e(TAG, urlString);



        new GetRisk().execute(urlString);

    }

    private class GetRisk extends AsyncTask<String, Void, String> {
        protected String doInBackground(String... urlString) {
            return getResponseBody(urlString[0]);
        }

        protected void onPostExecute(String result) {
            countCrimes(result);
            sendNotification();
        }

        private void countCrimes(String result) {
            int i = 0;
            Pattern p = Pattern.compile("category");
            Matcher m = p.matcher(result);
            while (m.find()) {
                i++;
            }

            totalCrimes = i;
            Log.e(TAG, String.valueOf(i));
        }
    }

    private String getResponseBody(final String urlString) {
        String result = null;
        try {

            URL url = new URL(urlString);
            HttpsURLConnection httpsURLConnection = (HttpsURLConnection) url.openConnection();
            httpsURLConnection.setRequestMethod("GET");

            BufferedReader reader = new BufferedReader(new InputStreamReader(httpsURLConnection.getInputStream()));

            StringBuffer resultBuffer = new StringBuffer();
            String line = "";

            while ((line = reader.readLine()) != null) {
                resultBuffer.append(line);
            }
            result = resultBuffer.toString();

        } catch (UnknownHostException | SocketException e) {

            if(retryAttemptsLeft --> 0) {

                System.err.printf("Could not connect to host - retrying in %d seconds... [%d/%d]%n", retryDelaySeconds, retryAttempts - retryAttemptsLeft, retryAttempts);

                try {

                    Thread.sleep(retryDelaySeconds * 1000);

                } catch (InterruptedException e1) {

                    e1.printStackTrace();
                }

                result = getResponseBody(urlString);

            }

        } catch (IOException e) {

            e.printStackTrace();

        } finally {

            retryAttemptsLeft = retryAttempts;
        }

        return result;
    }


    // called once to get the date from 3 months ago
    private void getDate() {
        // get current date in YYYY-MM format
        java.util.Date date= new Date();
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        int month = cal.get(Calendar.MONTH) - 2; // get report from 3 months ago - make sure report is valid
        int year = cal.get(Calendar.YEAR);

        dateValue = "&date=" + String.valueOf(year) + "-";

        if (month < 10)
            dateValue += ("0" + String.valueOf(month));
        else
            dateValue += String.valueOf(month);
    }
}