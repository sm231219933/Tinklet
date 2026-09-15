import java.util.Properties

val localProps = Properties().also { props ->
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { props.load(it) }
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.devtools.ksp)
    alias(libs.plugins.jetbrains.kotlin.plugin.serialization)
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.tinklet.bharatdatingapp"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.tinklet.bharatdatingapp"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        val truecallerKey = localProps.getProperty("TRUECALLER_KEY") ?: ""
        
        manifestPlaceholders["TRUECALLER_KEY"] = truecallerKey

        ndk {
            abiFilters.addAll(listOf("armeabi-v7a", "arm64-v8a"))
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true 
            isShrinkResources = true 
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }

    bundle {
        language { enableSplit = true }
        density { enableSplit = true }
        abi { enableSplit = true }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        isCoreLibraryDesugaringEnabled = true
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.accompanist.permissions)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.video)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.compose.adaptive)
    implementation(libs.androidx.compose.adaptive.layout)
    implementation(libs.androidx.compose.adaptive.navigation3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.navigation3.runtime)
    implementation("androidx.navigation3:navigation3-ui:1.0.1")
    implementation("androidx.room:room-ktx:2.7.0")
    implementation("androidx.room:room-runtime:2.7.0")
    
    // Networking & Backend
    implementation(libs.retrofit)
    implementation(libs.converter.moshi)
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation(libs.okhttp)
    implementation(libs.logging.interceptor)

    // Firebase & Calling
    implementation("io.getstream:stream-webrtc-android:1.1.1")
    implementation("com.google.firebase:firebase-storage-ktx:21.0.1")
    implementation(libs.kotlinx.coroutines.play.services)
    implementation("com.google.firebase:firebase-firestore-ktx:25.1.2")
    implementation(libs.firebase.auth)
    implementation("com.google.firebase:firebase-messaging-ktx:24.1.0")
    
    // GIF & Lottie Support
    implementation("io.coil-kt:coil-gif:2.7.0")
    implementation("com.airbnb.android:lottie-compose:6.5.0")
    
    // Identity & Google
    implementation(libs.androidx.credential.manager)
    implementation(libs.androidx.credential.manager.play.services)
    implementation(libs.google.id.token)
    implementation(libs.google.auth)
    implementation(libs.play.services.auth.phone)
    implementation(libs.truecaller.sdk)

    implementation("com.google.android.gms:play-services-ads:23.6.0")

    
    implementation(libs.webrtc)
    implementation(libs.socket.io)
    implementation(libs.coil.compose)
    implementation(libs.mlkit.image.labeling)
    implementation(libs.mlkit.face.detection)
    implementation(libs.retrofit)
    implementation(libs.converter.moshi)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.core)
    implementation(libs.logging.interceptor)
    implementation(libs.material)
    implementation(libs.moshi.kotlin)
    implementation(libs.play.services.location)
    
    testImplementation(libs.androidx.core)
    testImplementation(libs.androidx.junit)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.runner)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
    "ksp"(libs.androidx.room.compiler)
    "ksp"(libs.moshi.kotlin.codegen)
}
