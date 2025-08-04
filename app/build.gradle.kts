plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.casparvdbroek.securecam"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.casparvdbroek.securecam"
        minSdk = 24
        targetSdk = 33
        versionCode = 2
        versionName = "1.01"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {

    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    
    // HTTP server for WebRTC signaling
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    
    // JSON handling (Apache 2.0 License)
    implementation("com.google.code.gson:gson:2.10.1")
    
    // Network utilities (Apache 2.0 License)
    implementation("androidx.work:work-runtime:2.9.0")
    
    // WebSocket support (Apache 2.0 License)
    implementation("org.java-websocket:Java-WebSocket:1.5.3")
    
    // CameraX API (Built into Android - Apache 2.0 License)
    implementation("androidx.camera:camera-core:1.3.0")
    implementation("androidx.camera:camera-camera2:1.3.0")
    implementation("androidx.camera:camera-lifecycle:1.3.0")
    implementation("androidx.camera:camera-view:1.3.0")
    
    // ExifInterface for image metadata (Apache 2.0 License)
    implementation("androidx.exifinterface:exifinterface:1.3.6")
    
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}
