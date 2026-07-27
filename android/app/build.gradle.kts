plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "global.bert.widget"
    compileSdk = 36

    defaultConfig {
        applicationId = "global.bert.widget"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("String", "BERT_QUOTE_URL", "\"https://berthalla.io/widget/api/quote\"")
    }

    val releaseKeystore = file("/etc/bert-widget/bert-widget-release.jks")
    val releasePasswordFile = file("/etc/bert-widget/keystore.pass")
    signingConfigs {
        if (releaseKeystore.isFile && releasePasswordFile.isFile) {
            create("release") {
                storeFile = releaseKeystore
                val secret = releasePasswordFile.readText().trim()
                storePassword = secret
                keyAlias = "bert-widget"
                keyPassword = secret
            }
        }
    }

    buildTypes {
        debug {
            buildConfigField("String", "BERT_QUOTE_URL", "\"http://10.0.2.2:8787/v1/bert/quote\"")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures { compose = true; buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.06.00")
    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.12.3")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.glance:glance-appwidget:1.1.1")
    implementation("androidx.work:work-runtime:2.11.2")
}
