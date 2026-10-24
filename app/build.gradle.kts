plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.rescuefarm"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.nhom_7_mobileapp"
        minSdk = 29
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
        buildConfigField("String", "CLOUDINARY_CLOUD_NAME", "\"${providers.gradleProperty("CLOUDINARY_CLOUD_NAME").orElse("").get()}\"")
        buildConfigField("String", "CLOUDINARY_SIGNING_URL", "\"${providers.gradleProperty("CLOUDINARY_SIGNING_URL").orElse("").get()}\"")
        buildConfigField("String", "RENDER_BACKEND_URL", "\"${providers.gradleProperty("RENDER_BACKEND_URL").orElse("").get()}\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        javaCompileOptions {
            annotationProcessorOptions {
                arguments += mapOf("room.schemaLocation" to "$projectDir/schemas")
            }
        }
    }
    buildFeatures { buildConfig = true }
    testBuildType = providers.gradleProperty("reportTestBuildType").orElse("debug").get()

    buildTypes {
        create("reportDemo") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".reportdemo"
            matchingFallbacks += listOf("debug")
            buildConfigField("String", "RENDER_BACKEND_URL", "\"http://10.0.2.2:3008\"")
            buildConfigField("String", "CLOUDINARY_CLOUD_NAME", "\"\"")
            buildConfigField("String", "CLOUDINARY_SIGNING_URL", "\"http://10.0.2.2:3008/media-disabled\"")
        }
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.material)
    implementation(libs.viewpager2)
    implementation(libs.fragment)
    implementation(libs.lifecycle.livedata)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.navigation.fragment)
    implementation(libs.navigation.ui)
    implementation(libs.room.runtime)
    implementation(libs.credentials)
    implementation(libs.credentials.play.services.auth)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.appcheck.playintegrity)
    debugImplementation(libs.firebase.appcheck.debug)
    implementation(libs.googleid)
    implementation(libs.play.services.location)
    implementation(libs.glide)
    implementation(libs.work.runtime)
    annotationProcessor(libs.room.compiler)
    testImplementation(libs.junit)
    testImplementation(libs.arch.core.testing)
    testImplementation(libs.room.testing)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
}
