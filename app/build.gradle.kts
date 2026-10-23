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
        versionCode = runCatching {
            val proc = ProcessBuilder("git", "rev-list", "--count", "HEAD")
                .directory(rootDir)
                .redirectErrorStream(true)
                .start()
            proc.inputStream.bufferedReader().readText().trim().toInt()
        }.getOrDefault(1).coerceAtLeast(1)
        versionName = "1.0.0"

        // Répertoire du dépôt contenant index.json et les catalogues JSON.
        // Aucune source de média n'est incluse dans l'APK : l'index démarre vide.
        val jsonSourceBaseUrl = (project.findProperty("jsonSourceBaseUrl") as String?)
            ?: "https://raw.githubusercontent.com/missamedia69-code/MISSA_TV_360/main/sources/"
        buildConfigField("String", "JSON_SOURCE_BASE_URL", "\"$jsonSourceBaseUrl\"")
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
    testImplementation("junit:junit:4.13.2")
    // AndroidX de base
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.fragment:fragment-ktx:1.8.5")

    // Leanback (interface Android TV : browse, détails, cartes)
    implementation("androidx.leanback:leanback:1.2.0")

    // Media3 / ExoPlayer (lecture vidéo + HLS)
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")
    implementation("androidx.media3:media3-session:1.4.1")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Extensions : contrat binaire partagé avec les plugins téléchargés.
    implementation(project(":plugin-api"))
    implementation(project(":cloudstream-compat"))
    // Lecture des JSON du catalogue et des dépôts d'extensions manuels.
    implementation("com.google.code.gson:gson:2.11.0")
    // Écran Extensions (liste pilotable à la télécommande).
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
}
