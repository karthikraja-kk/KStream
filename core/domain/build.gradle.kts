plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.kstream.core.domain"
    compileSdk = 34

    defaultConfig {
        minSdk = 25
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:model"))
    
    implementation(libs.androidx.core.ktx)
    implementation(libs.hilt.android)
    implementation(libs.media3.common)
    implementation(libs.media3.exoplayer)
    ksp(libs.hilt.compiler)
    
    testImplementation(libs.junit)
}
