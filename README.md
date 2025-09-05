 MapApp - GreenPulse Module

MapApp is an Android application module that provides **real-time traffic light monitoring** and **user location tracking** using **OSMDroid** and **Firebase Realtime Database**.
The app displays the status of traffic lights within a **3km radius** of the user's current location.


## Features

- *Real-time Location Tracking*– Continuously updates the user's current location.
- *Traffic Lights Monitoring* – Fetches real-time traffic light locations from Firebase.
- *3km Radius Filter* – Only displays nearby traffic lights within 3km.
- *Dynamic Markers* – Red, Green, and Yellow markers based on traffic light status.
- *Radius Visualization* – a transparent 3km circle around the user's location.


## Tech Stack / Dependencies

- **Programming Language:** Java  
- **Android SDK:** minSdk 24, targetSdk 34  
- **Libraries & Tools:**
  - [OSMDroid](https://github.com/osmdroid/osmdroid) – For map rendering
  - [Firebase Realtime Database](https://firebase.google.com/docs/database) – For traffic light data
  - [AndroidX Lifecycle](https://developer.android.com/jetpack/androidx/releases/lifecycle) – ViewModel & LiveData
  - AndroidX AppCompat, Material Design, ConstraintLayout, Preferences
  - Google Play Services - Location


