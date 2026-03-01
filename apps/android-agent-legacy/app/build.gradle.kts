plugins {
  id("com.android.application")
  id("org.jetbrains.kotlin.android")
}
android {
  namespace = "cv.rootnode.ambient.legacy"
  compileSdk = 34
  defaultConfig {
    applicationId = "cv.rootnode.ambient.legacy"
    minSdk = 19
    targetSdk = 28
    versionCode = 1
    versionName = "0.1.0-legacy"
  }
  buildTypes { release { isMinifyEnabled = false } }
  compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
  kotlinOptions { jvmTarget = "17" }
}
dependencies {}
