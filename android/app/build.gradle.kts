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
        versionCode = 13
        versionName = "0.3.9"
        buildConfigField("String", "BERT_QUOTE_URL", "\"https://berthalla.io/widget/api/quote\"")
    }

    val releaseKeystore = file(providers.gradleProperty("BERT_RELEASE_KEYSTORE").getOrElse("/etc/bert-widget/bert-widget-release.jks"))
    val releasePasswordFile = file(providers.gradleProperty("BERT_RELEASE_PASSWORD_FILE").getOrElse("/etc/bert-widget/keystore.pass"))
    val releaseSigningAvailable = releaseKeystore.isFile && releasePasswordFile.isFile
    signingConfigs {
        create("release") {
            storeFile = releaseKeystore
            val secret = if (releaseSigningAvailable) releasePasswordFile.readText().trim() else ""
            storePassword = secret
            keyAlias = "bert-widget"
            keyPassword = secret
        }
    }

    gradle.taskGraph.whenReady {
        val releaseRequested = allTasks.any { task ->
            task.project == project && task.name.contains("release", ignoreCase = true)
        }
        if (releaseRequested && !releaseSigningAvailable) {
            throw GradleException(
                "Release signing material is required. Expected keystore at ${releaseKeystore.absolutePath} " +
                    "and password file at ${releasePasswordFile.absolutePath}.",
            )
        }
    }

    buildTypes {
        debug {
            buildConfigField("String", "BERT_QUOTE_URL", "\"http://10.0.2.2:8787/v1/bert/quote\"")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
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
    testImplementation("junit:junit:4.13.2")
}
