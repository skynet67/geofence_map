package com.masterofcode.geofence;

import android.app.Activity;
import android.app.Dialog;
import android.content.*;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.Log;
import android.widget.Toast;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesClient;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.LocationClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.model.*;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity implements LocationListener,
        GooglePlayServicesClient.ConnectionCallbacks,
        GooglePlayServicesClient.OnConnectionFailedListener {

    private static String url = "https://dl.dropboxusercontent.com/s/zs9x96ml35gq98q/geodata.json";

    private GoogleMap mMap;

    // A request to connect to Location Services
    private LocationRequest mLocationRequest;

    // Stores the current instantiation of the location client in this object
    private LocationClient mLocationClient;

    // Store the current request
    private GeofenceUtils.REQUEST_TYPE mRequestType;

    // Store the current type of removal
    private GeofenceUtils.REMOVE_TYPE mRemoveType;

    // Persistent storage for geofences
    private SimpleGeofenceStore mPrefs;

    // Store a list of geofences to add
    List<Geofence> mCurrentGeofences;

    // Add geofences handler
    private GeofenceRequester mGeofenceRequester;
    // Remove geofences handler
    private GeofenceRemover mGeofenceRemover;

    /*
    * An instance of an inner class that receives broadcasts from listeners and from the
    * IntentService that receives geofence transition events
    */
    private GeofenceSampleReceiver mBroadcastReceiver;

    // An intent filter for the broadcast receiver
    private IntentFilter mIntentFilter;

    // Store the list of geofences to remove
    private List<String> mGeofenceIdsToRemove;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initLocationListener();
        initMap();

        initGeofences();
    }

    private void initLocationListener() {
        // Create a new global location parameters object
        mLocationRequest = LocationRequest.create();

        /*
         * Set the update interval
         */
        mLocationRequest.setInterval(LocationUtils.UPDATE_INTERVAL_IN_MILLISECONDS);

        // Use high accuracy
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        // Set the interval ceiling to one minute
        mLocationRequest.setFastestInterval(LocationUtils.FAST_INTERVAL_CEILING_IN_MILLISECONDS);

        /*
         * Create a new location client, using the enclosing class to
         * handle callbacks.
         */
        mLocationClient = new LocationClient(this, this, this);
    }

    private void initMap() {
        // Do a null check to confirm that we have not already instantiated the map.
        if (mMap == null) {
            // Try to obtain the map from the SupportMapFragment.
            mMap = ((MapFragment) getFragmentManager().findFragmentById(R.id.map)).getMap();
            // Check if we were successful in obtaining the map.
            if (mMap != null) {
                mMap.setMyLocationEnabled(true);
            }
        }
    }

    private void initGeofences() {
        // Create a new broadcast receiver to receive updates from the listeners and service
        mBroadcastReceiver = new GeofenceSampleReceiver();

        // Create an intent filter for the broadcast receiver
        mIntentFilter = new IntentFilter();

        // Action for broadcast Intents that report successful addition of geofences
        mIntentFilter.addAction(GeofenceUtils.ACTION_GEOFENCES_ADDED);

        // Action for broadcast Intents that report successful removal of geofences
        mIntentFilter.addAction(GeofenceUtils.ACTION_GEOFENCES_REMOVED);

        // Action for broadcast Intents containing various types of geofencing errors
        mIntentFilter.addAction(GeofenceUtils.ACTION_GEOFENCE_ERROR);

        // All Location Services sample apps use this category
        mIntentFilter.addCategory(GeofenceUtils.CATEGORY_LOCATION_SERVICES);

        // Instantiate a new geofence storage area
        mPrefs = new SimpleGeofenceStore(this);

        // Instantiate the current List of geofences
        mCurrentGeofences = new ArrayList<Geofence>();

        // Instantiate a Geofence requester
        mGeofenceRequester = new GeofenceRequester(this);

        // Instantiate a Geofence remover
        mGeofenceRemover = new GeofenceRemover(this);

        new ReadJsonAsyncTask().execute();
    }

    /**
     * Verify that Google Play services is available before making a request.
     *
     * @return true if Google Play services is available, otherwise false
     */
    private boolean servicesConnected() {

        // Check that Google Play services is available
        int resultCode =
                GooglePlayServicesUtil.isGooglePlayServicesAvailable(this);

        // If Google Play services is available
        if (ConnectionResult.SUCCESS == resultCode) {
            // In debug mode, log the status
            Log.d("LocationUtils", getString(R.string.play_services_available));

            // Continue
            return true;
            // Google Play services was not available for some reason
        } else {
            // Display an error dialog
            Dialog dialog = GooglePlayServicesUtil.getErrorDialog(resultCode, this, 0);
            if (dialog != null) {
                ErrorDialogFragment errorFragment = new ErrorDialogFragment();
                errorFragment.setDialog(dialog);
                errorFragment.show(getFragmentManager(), "LocationUtils");
            }
            return false;
        }
    }

    /**
     * In response to a request to start updates, send a request
     * to Location Services
     */
    private void startPeriodicUpdates() {
        if (servicesConnected()) {
            mLocationClient.requestLocationUpdates(mLocationRequest, this);
        }
    }

    /**
     * In response to a request to stop updates, send a request to
     * Location Services
     */
    private void stopPeriodicUpdates() {
        mLocationClient.removeLocationUpdates(this);
    }

    @Override
    public void onResume() {
        super.onResume();

        LocalBroadcastManager.getInstance(this).registerReceiver(mBroadcastReceiver, mIntentFilter);

        initMap();

        /*
        * Connect the client. Don't re-start any requests here;
        * instead, wait for onResume()
        */
        mLocationClient.connect();
    }

    @Override
    public void onPause() {
        super.onPause();

        LocalBroadcastManager.getInstance(this).unregisterReceiver(mBroadcastReceiver);

        // After disconnect() is called, the client is considered "dead".
        if (mLocationClient != null && mLocationClient.isConnected()) {
            stopPeriodicUpdates();
            mLocationClient.disconnect();
        }

    }

    @Override
    public void onDestroy(){
        super.onDestroy();

        unregisterGeofences();
    }

    /*
     * Handle results returned to this Activity by other Activities started with
     * startActivityForResult(). In particular, the method onConnectionFailed() in
     * LocationUpdateRemover and LocationUpdateRequester may call startResolutionForResult() to
     * start an Activity that handles Google Play services problems. The result of this
     * call returns here, to onActivityResult.
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {

        // Choose what to do based on the request code
        switch (requestCode) {

            // If the request code matches the code sent in onConnectionFailed
            case LocationUtils.CONNECTION_FAILURE_RESOLUTION_REQUEST:

                switch (resultCode) {
                    // If Google Play services resolved the problem
                    case Activity.RESULT_OK:
                        // If the request was to add geofences
                        if (GeofenceUtils.REQUEST_TYPE.ADD == mRequestType) {

                            // Toggle the request flag and send a new request
                            mGeofenceRequester.setInProgressFlag(false);

                            // Restart the process of adding the current geofences
                            mGeofenceRequester.addGeofences(mCurrentGeofences);

                            // If the request was to remove geofences
                        } else if (GeofenceUtils.REQUEST_TYPE.REMOVE == mRequestType) {

                            // Toggle the removal flag and send a new removal request
                            mGeofenceRemover.setInProgressFlag(false);

                            // If the removal was by Intent
                            if (GeofenceUtils.REMOVE_TYPE.INTENT == mRemoveType) {

                                // Restart the removal of all geofences for the PendingIntent
                                mGeofenceRemover.removeGeofencesByIntent(
                                        mGeofenceRequester.getRequestPendingIntent());

                                // If the removal was by a List of geofence IDs
                            } else {

                                // Restart the removal of the geofence list
                                mGeofenceRemover.removeGeofencesById(mGeofenceIdsToRemove);
                            }
                        }
                        break;

                    // If any other result was returned by Google Play services
                    default:
                        Log.d("LocationUtils", getString(R.string.no_resolution));
                        break;
                }

                // If any other request code was received
            default:
                // Report that this Activity received an unknown requestCode
                Log.d("LocationUtils", getString(R.string.unknown_activity_request_code, requestCode));
                break;
        }
    }

    @Override
    public void onConnected(Bundle bundle) {
        startPeriodicUpdates();

        if (mLocationClient != null && mLocationClient.isConnected() && mMap != null) {

            Location lastLoc = mLocationClient.getLastLocation();
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(lastLoc.getLatitude(), lastLoc.getLongitude()), 16));
        }
    }

    @Override
    public void onDisconnected() {

    }

    @Override
    public void onLocationChanged(Location location) {

    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
          /*
         * Google Play services can resolve some errors it detects.
         * If the error has a resolution, try sending an Intent to
         * start a Google Play services activity that can resolve
         * error.
         */
        if (connectionResult.hasResolution()) {
            try {

                // Start an Activity that tries to resolve the error
                connectionResult.startResolutionForResult(
                        this,
                        LocationUtils.CONNECTION_FAILURE_RESOLUTION_REQUEST);

                /*
                * Thrown if Google Play services canceled the original
                * PendingIntent
                */

            } catch (IntentSender.SendIntentException e) {

                // Log the error
                e.printStackTrace();
            }
        } else {

            // If no resolution is available, display a dialog to the user with the error.
            showErrorDialog(connectionResult.getErrorCode());
        }
    }


    public void registerGeofences() {
    /*
                * Record the request as an ADD. If a connection error occurs,
                * the app can automatically restart the add request if Google Play services
                * can fix the error
                */
    mRequestType = GeofenceUtils.REQUEST_TYPE.ADD;

    if (!servicesConnected()) {
        return;
    }

    // Start the request. Fail if there's already a request in progress
    try {
        // Try to add geofences
        mGeofenceRequester.addGeofences(mCurrentGeofences);
    } catch (UnsupportedOperationException e) {
        // Notify user that previous request hasn't finished.
        Toast.makeText(MainActivity.this, R.string.add_geofences_already_requested_error,
                Toast.LENGTH_LONG).show();
    }
    }

    public void unregisterGeofences() {
        /*
         * Remove the geofence by creating a List of geofences to
         * remove and sending it to Location Services. The List
         * contains the id of geofence 1 ("1").
         * The removal happens asynchronously; Location Services calls
         * onRemoveGeofencesByPendingIntentResult() (implemented in
         * the current Activity) when the removal is done.
         */

        mGeofenceIdsToRemove = new ArrayList<String>();
        for(Geofence g : mCurrentGeofences){
            mGeofenceIdsToRemove.add(g.getRequestId());
        }

        /*
         * Record the removal as remove by list. If a connection error occurs,
         * the app can automatically restart the removal if Google Play services
         * can fix the error
         */
        mRemoveType = GeofenceUtils.REMOVE_TYPE.LIST;

        /*
         * Check for Google Play services. Do this after
         * setting the request type. If connecting to Google Play services
         * fails, onActivityResult is eventually called, and it needs to
         * know what type of request was in progress.
         */
        if (!servicesConnected()) {

            return;
        }

        // Try to remove the geofence
        try {
            mGeofenceRemover.removeGeofencesById(mGeofenceIdsToRemove);

            // Catch errors with the provided geofence IDs
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        } catch (UnsupportedOperationException e) {
            // Notify user that previous request hasn't finished.
            Toast.makeText(this, R.string.remove_geofences_already_requested_error,
                    Toast.LENGTH_LONG).show();
        }
    }

    private void showOnMap(List<SimpleGeofence> geofencesList){
        if(mMap != null){

            for(SimpleGeofence sg : geofencesList){

                LatLng center = new LatLng(sg.getLatitude(), sg.getLongitude());

                BitmapDescriptor bitmapDescriptor = null;

                switch (sg.getTransitionType()){
                    case 1:
                        bitmapDescriptor = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE);
                        break;
                    case 2:
                        bitmapDescriptor = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED);
                        break;
                }

                Marker centerMarker = mMap.addMarker(new MarkerOptions()
                        .position(center)
                        .draggable(false)
                        .icon(bitmapDescriptor));

                Circle circle = mMap.addCircle(new CircleOptions()
                        .center(center)
                        .radius(sg.getRadius())
                        .strokeWidth(2)
                        .strokeColor(R.color.stroke)
                        .fillColor(R.color.fill));
            }
        }
    }

    /**
     * Define a Broadcast receiver that receives updates from connection listeners and
     * the geofence transition service.
     */
    public class GeofenceSampleReceiver extends BroadcastReceiver {
        /*
         * Define the required method for broadcast receivers
         * This method is invoked when a broadcast Intent triggers the receiver
         */
        @Override
        public void onReceive(Context context, Intent intent) {

            // Check the action code and determine what to do
            String action = intent.getAction();

            // Intent contains information about errors in adding or removing geofences
            if (TextUtils.equals(action, GeofenceUtils.ACTION_GEOFENCE_ERROR)) {

                handleGeofenceError(context, intent);

                // Intent contains information about successful addition or removal of geofences
            } else if (
                    TextUtils.equals(action, GeofenceUtils.ACTION_GEOFENCES_ADDED)
                            ||
                            TextUtils.equals(action, GeofenceUtils.ACTION_GEOFENCES_REMOVED)) {

                handleGeofenceStatus(context, intent);

                // Intent contains information about a geofence transition
            } else if (TextUtils.equals(action, GeofenceUtils.ACTION_GEOFENCE_TRANSITION)) {

                handleGeofenceTransition(context, intent);

                // The Intent contained an invalid action
            } else {
                Log.e(GeofenceUtils.APPTAG, getString(R.string.invalid_action_detail, action));
                Toast.makeText(context, R.string.invalid_action, Toast.LENGTH_LONG).show();
            }
        }

        /**
         * If you want to display a UI message about adding or removing geofences, put it here.
         *
         * @param context A Context for this component
         * @param intent  The received broadcast Intent
         */
        private void handleGeofenceStatus(Context context, Intent intent) {

        }

        /**
         * Report geofence transitions to the UI
         *
         * @param context A Context for this component
         * @param intent  The Intent containing the transition
         */
        private void handleGeofenceTransition(Context context, Intent intent) {
            /*
             * If you want to change the UI when a transition occurs, put the code
             * here. The current design of the app uses a notification to inform the
             * user that a transition has occurred.
             */
        }

        /**
         * Report addition or removal errors to the UI, using a Toast
         *
         * @param intent A broadcast Intent sent by ReceiveTransitionsIntentService
         */
        private void handleGeofenceError(Context context, Intent intent) {
            String msg = intent.getStringExtra(GeofenceUtils.EXTRA_GEOFENCE_STATUS);
            Log.e(GeofenceUtils.APPTAG, msg);
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Show a dialog returned by Google Play services for the
     * connection error code
     *
     * @param errorCode An error code returned from onConnectionFailed
     */
    private void showErrorDialog(int errorCode) {

        // Get the error dialog from Google Play services
        Dialog errorDialog = GooglePlayServicesUtil.getErrorDialog(
                errorCode,
                this,
                LocationUtils.CONNECTION_FAILURE_RESOLUTION_REQUEST);

        // If Google Play services can provide an error dialog
        if (errorDialog != null) {

            // Create a new DialogFragment in which to show the error dialog
            ErrorDialogFragment errorFragment = new ErrorDialogFragment();

            // Set the dialog in the DialogFragment
            errorFragment.setDialog(errorDialog);

            // Show the error dialog in the DialogFragment
            errorFragment.show(getFragmentManager(), "LocationUtils");
        }
    }

    private class ReadJsonAsyncTask extends AsyncTask<Void, Void, List<SimpleGeofence>> {

        @Override
        protected List<SimpleGeofence> doInBackground(Void... params) {
            JSONObject response = readJsonFromUrl();

            List<SimpleGeofence> geofences = new ArrayList<SimpleGeofence>();

            if (response != null) {
                if (!response.isNull("geocoords")) {
                    try {
                        JSONArray records = response.getJSONArray("geocoords");
                        for (int i = 0; i < records.length(); i++) {
                            JSONObject obj = records.getJSONObject(i);

                            String id = obj.getString("id");
                            double lat = obj.getDouble("latitude");
                            double lon = obj.getDouble("longitude");
                            int radius = obj.getInt("radius");
                            long expiration = obj.getLong("expiredMins") * DateUtils.MINUTE_IN_MILLIS;
                            int transiiton = obj.getInt("transition");

                            geofences.add(new SimpleGeofence(id, lat, lon, radius, expiration, transiiton));
                        }

                        return geofences;

                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }

            return null;
        }


        private JSONObject readJsonFromUrl() {

            try {
                HttpClient client = new DefaultHttpClient();
                HttpGet request = new HttpGet(url);
                String content = client.execute(request, new BasicResponseHandler());
                if (!TextUtils.isEmpty(content)) {
                    return new JSONObject(content);
                }
            } catch (ClientProtocolException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (JSONException e) {
                e.printStackTrace();
            }

            return null;
        }

        @Override
        protected void onPostExecute(List<SimpleGeofence> response) {
            super.onPostExecute(response);

            if (response != null) {

                for (SimpleGeofence sg : response){
                    mCurrentGeofences.add(sg.toGeofence());
                }

                registerGeofences();
                showOnMap(response);
            }
        }
    }
}
