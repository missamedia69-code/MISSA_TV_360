plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.missa.tv360"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.missa.tv360"
        minSdk = 21
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        // URL du catalogue (clean-room) — le dépôt GitHub / GitHub Pages en
        // est le « serveur ». Surchargeable au build :
        //   ./gradlew assembleDebug -PbffBaseUrl=http://10.0.2.2:8765/
        val bffUrl = (project.findProperty("bffBaseUrl") as String?)
            ?: "https://missamedia69-code.github.io/MISSA_TV_360/"
        buildConfigField("String", "BFF_BASE_URL", "\"$bffUrl\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
        }
        // Variante isolée pour tester le catalogue de la branche sans modifier
        // l'application stable ni l'URL publique GitHub Pages.
        create("preview") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".preview"
            versionNameSuffix = "-preview"
            resValue("string", "app_name", "MISSA TV 360 Preview")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        buildConfig = true
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
                "META-INF/AL2.0",
                "META-INF/LGPL2.1"
            )
        }
    }
}

dependencies {
    // AndroidX de base
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.fragment:fragment-ktx:1.8.5")

    // Leanback (interface Android TV : browse, détails, cartes)
    implementation("androidx.leanback:leanback:1.0.0")

    // Media3 / ExoPlayer (lecture vidéo + HLS)
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")
    implementation("androidx.media3:media3-session:1.4.1")

    // Couche API BFF (Retrofit + coroutines)
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
