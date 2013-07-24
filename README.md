Simple application that shows Geofence objects on the MapFragment
============

What is Geofence
------------
Geofencing combines awareness of the user's current location with awareness of nearby features, defined as the user's proximity to locations that may be of interest. To mark a location of interest, you specify its latitude and longitude. To adjust the proximity for the location, you add a radius. The latitude, longitude, and radius define a geofence. You can have multiple active geofences at one time.


How to run
------------
- Open AndroidStudio 0.2.x;
- File -> Import project from external model -> Gradle;
- Change com.google.android.maps.v2.API_KEY to yours in AndroidManifest.xml
- Change url link to the json file. App users current link https://dl.dropboxusercontent.com/s/zs9x96ml35gq98q/geodata.json with a test data.
- Build & Run.
