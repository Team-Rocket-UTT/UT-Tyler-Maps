import java.util.Properties
val localProps = Properties().apply{
    val file = rootProject.file("local.properties")
    if(file.exists()){
        load(file.inputStream())
    }
}
plugins {
    alias(libs.plugins.android.application)
    id("org.jetbrains.dokka")
    id("org.jetbrains.dokka-javadoc")
}

android {


    namespace = "com.teamrocket.uttylermaps"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.teamrocket.uttylermaps"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "MAPPEDIN_KEY", "\"${localProps["MAPPEDIN_KEY"]}\"")
        buildConfigField("String", "MAPPEDIN_SECRET", "\"${localProps["MAPPEDIN_SECRET"]}\"")
        buildConfigField("String", "MAPPEDIN_MAP_ID", "\"${localProps["MAPPEDIN_MAP_ID"]}\"")


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
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.mappedin)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation("com.indooratlas.android:indooratlas-android-sdk:3.8.0@aar")
    implementation(libs.androidx.preference)
    implementation(libs.androidx.espresso.core)
    implementation(libs.androidx.remote.creation.core)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

}
dokka {
    moduleName.set("UT Tyler Maps")
    dokkaPublications.configureEach {
        suppressInheritedMembers.set(true)
    }
}

tasks.named("dokkaGenerateHtml") {
    dependsOn("assembleDebug")
}//removed unused methods from imports