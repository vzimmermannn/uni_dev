plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
 namespace = "br.com.eventoperto"
 compileSdk = 35
 defaultConfig { applicationId = "br.com.eventoperto"; minSdk = 26; targetSdk = 35; versionCode = 1; versionName = "0.1" }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget = "17" }
}
dependencies { implementation("com.google.android.gms:play-services-nearby:19.3.0") }
