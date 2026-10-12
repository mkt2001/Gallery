#!/bin/bash
B=app/src/main/java/com/mahesh/gallerypro
mkdir -p $B/data $B/ui app/src/main/res/drawable app/src/main/res/values
mv -f GalleryApplication.kt MainActivity.kt $B/
mv -f Models.kt MediaRepository.kt GalleryViewModel.kt VaultStore.kt $B/data/
mv -f Components.kt Editor.kt Home.kt Theme.kt Trash.kt Utils.kt Vault.kt Viewer.kt $B/ui/
mv -f AndroidManifest.xml app/src/main/
mv -f ic_launcher.xml app/src/main/res/drawable/
mv -f themes.xml app/src/main/res/values/
cat > settings.gradle.kts <<'EOF'
pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "GalleryPro"
include(":app")
EOF
cat > build.gradle.kts <<'EOF'
plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
}
EOF
cat > gradle.properties <<'EOF'
org.gradle.jvmargs=-Xmx3g -Dfile.encoding=UTF-8
android.useAndroidX=true
android.nonTransitiveRClass=true
kotlin.code.style=official
EOF
cat > app/build.gradle.kts <<'EOF'
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.mahesh.gallerypro"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.mahesh.gallerypro"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.5")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.5")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("io.coil-kt:coil-video:2.7.0")
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")
    implementation("androidx.fragment:fragment-ktx:1.8.3")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
}
EOF

# ---- launcher icon (adaptive, gradient) ----
mkdir -p app/src/main/res/mipmap-anydpi-v26
cat > app/src/main/res/drawable/ic_launcher_background.xml <<'EOF'
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="108dp" android:height="108dp"
    android:viewportWidth="108" android:viewportHeight="108">
    <path android:pathData="M0,0h108v108h-108z">
        <aapt:attr name="android:fillColor">
            <gradient
                android:startX="0" android:startY="0"
                android:endX="108" android:endY="108"
                android:type="linear">
                <item android:color="#4A2BD6" android:offset="0.0" />
                <item android:color="#D81B73" android:offset="0.55" />
                <item android:color="#FF9800" android:offset="1.0" />
            </gradient>
        </aapt:attr>
    </path>
</vector>
EOF
cat > app/src/main/res/drawable/ic_launcher_foreground.xml <<'EOF'
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="108dp" android:height="108dp"
    android:viewportWidth="108" android:viewportHeight="108">
    <!-- back photo card (tilted) -->
    <group android:rotation="-14" android:pivotX="54" android:pivotY="54">
        <path
            android:fillColor="#66FFFFFF"
            android:pathData="M36,32 H72 A6,6 0 0 1 78,38 V70 A6,6 0 0 1 72,76 H36 A6,6 0 0 1 30,70 V38 A6,6 0 0 1 36,32 Z" />
    </group>
    <!-- front photo card -->
    <path
        android:fillColor="#FFFFFF"
        android:pathData="M36,32 H72 A6,6 0 0 1 78,38 V70 A6,6 0 0 1 72,76 H36 A6,6 0 0 1 30,70 V38 A6,6 0 0 1 36,32 Z" />
    <!-- sky -->
    <path android:pathData="M37,36 H71 A3,3 0 0 1 74,39 V69 A3,3 0 0 1 71,72 H37 A3,3 0 0 1 34,69 V39 A3,3 0 0 1 37,36 Z">
        <aapt:attr name="android:fillColor">
            <gradient
                android:startX="54" android:startY="36"
                android:endX="54" android:endY="72"
                android:type="linear">
                <item android:color="#4FC3F7" android:offset="0.0" />
                <item android:color="#FFE0B2" android:offset="1.0" />
            </gradient>
        </aapt:attr>
    </path>
    <!-- sun -->
    <path
        android:fillColor="#FFD54F"
        android:pathData="M60.5,45 a4.5,4.5 0 1 0 9,0 a4.5,4.5 0 1 0 -9,0 Z" />
    <!-- far mountain -->
    <path
        android:fillColor="#7E57C2"
        android:pathData="M50,72 L62,56 L73.5,72 Z" />
    <!-- near mountain -->
    <path
        android:fillColor="#4527A0"
        android:pathData="M34.5,72 L47,52 L60,72 Z" />
</vector>
EOF
cat > app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml <<'EOF'
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
EOF
cat > app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml <<'EOF'
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
EOF
sed -i 's#android:icon="@drawable/ic_launcher"#android:icon="@mipmap/ic_launcher" android:roundIcon="@mipmap/ic_launcher_round"#' app/src/main/AndroidManifest.xml
