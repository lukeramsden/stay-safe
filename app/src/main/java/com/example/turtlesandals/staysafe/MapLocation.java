package com.example.turtlesandals.staysafe;

import android.content.Context;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.MapsInitializer;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

import static com.example.turtlesandals.staysafe.GPSService.LOCATION_DISTANCE;
import static com.example.turtlesandals.staysafe.GPSService.LOCATION_INTERVAL;

public class MapLocation extends Fragment {
    private static final String TAG = "MapLocation";
    MapView mMapView;
    private GoogleMap googleMap;
    private LocationManager mLocationManager = null;

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
            moveToLocation(location);
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

    MapLocation.LocationListener[] mLocationListeners = new MapLocation.LocationListener[] {
            new MapLocation.LocationListener(LocationManager.GPS_PROVIDER),
            new MapLocation.LocationListener(LocationManager.NETWORK_PROVIDER)
    };

    private void initializeLocationManager() {
        Log.e(TAG, "initializeLocationManager");
        if (mLocationManager == null) {
            mLocationManager = (LocationManager) getActivity().getApplicationContext().getSystemService(Context.LOCATION_SERVICE);
        }
    }

    public void moveToLocation(Location loc) {
        if(loc == null || googleMap == null) return;
        LatLng latlng = new LatLng(loc.getLatitude(), loc.getLongitude());
        CameraPosition cameraPosition = new CameraPosition.Builder().target(latlng).zoom(15).build();
        googleMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.location_fragment, container, false);

        mMapView = (MapView) rootView.findViewById(R.id.mapView);
        mMapView.onCreate(savedInstanceState);
        mMapView.setClickable(false);

        mMapView.onResume(); // needed to get the map to display immediately

        try {
            MapsInitializer.initialize(getActivity().getApplicationContext());
        } catch (Exception e) {
            e.printStackTrace();
        }

        initializeLocationManager();

        mMapView.getMapAsync(new OnMapReadyCallback() {
            @Override
            public void onMapReady(GoogleMap mMap) {
                Log.d(TAG, "onMapReady");

                googleMap = mMap;

                // For showing a move to my location button
                try {
                    googleMap.setMyLocationEnabled(true);
                } catch (java.lang.SecurityException ex) {
                    Log.i(TAG, "fail to request location update, ignore", ex);
                } catch (IllegalArgumentException ex) {
                    Log.d(TAG, "network provider does not exist, " + ex.getMessage());
                }

                try {
                    mLocationManager.requestLocationUpdates(
                            LocationManager.NETWORK_PROVIDER, LOCATION_INTERVAL, LOCATION_DISTANCE,
                            mLocationListeners[1]);
                    moveToLocation(mLocationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER));
                } catch (java.lang.SecurityException ex) {
                    Log.i(TAG, "fail to request location update, ignore", ex);
                } catch (IllegalArgumentException ex) {
                    Log.d(TAG, "network provider does not exist, " + ex.getMessage());
                }
                try {
                    mLocationManager.requestLocationUpdates(
                            LocationManager.GPS_PROVIDER, LOCATION_INTERVAL, LOCATION_DISTANCE,
                            mLocationListeners[0]);
                    moveToLocation(mLocationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER));
                } catch (java.lang.SecurityException ex) {
                    Log.i(TAG, "fail to request location update, ignore", ex);
                } catch (IllegalArgumentException ex) {
                    Log.d(TAG, "gps provider does not exist " + ex.getMessage());
                }
            }
        });

        return rootView;
    }

    @Override
    public void onResume() {
        super.onResume();
        mMapView.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        mMapView.onPause();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mMapView.onDestroy();
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

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        mMapView.onLowMemory();
    }
}
