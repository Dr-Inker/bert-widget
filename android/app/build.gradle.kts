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
        versionCode = 14
        versionName = "0.4.0"
        buildConfigField("String", "BERT_QUOTE_URL", "\"https://berthalla.io/widget/api/quote\"")
        buildConfigField("String", "BERT_ACTIVITY_URL", "\"https://berthalla.io/status.json\"")
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
            buildConfigField("String", "BERT_ACTIVITY_URL", "\"http://10.0.2.2:8787/v1/bert/activity\"")
        }
        create("preview") {
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
            applicationIdSuffix = ".preview"
            versionNameSuffix = "-preview"
            matchingFallbacks += "debug"
            // Keep default public HTTPS endpoints and main's cleartext restriction.
            // Debug's emulator fixtures and test activity manifest are not included.
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            it.maxHeapSize = "1024m"
            it.maxParallelForks = 1
            val scratch = layout.buildDirectory.dir("tmp/host-tests").get().asFile
            it.systemProperty("java.io.tmpdir", scratch.absolutePath)
            it.doFirst { scratch.mkdirs() }
            it.systemProperty("robolectric.dependency.repo.url", "https://repo.maven.apache.org/maven2")
            it.jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED", "--add-opens=java.base/java.util=ALL-UNNAMED")
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
    testImplementation(composeBom)
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    testImplementation("org.robolectric:robolectric:4.16.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20250517")
}
