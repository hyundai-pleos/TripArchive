plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.ktlint)
}

ktlint {
    version.set("1.8.0")
    filter {
        exclude { it.file.path.contains("/build/") || it.file.path.contains("/generated/") }
    }
}

android {
    namespace = "com.triparchive"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.triparchive"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    testBuildType = providers.gradleProperty("testBuildType").getOrElse("debug")

    buildTypes {
        debug {
            buildConfigField("boolean", "ARRIVAL_DEMO", "false")
        }
        release {
            buildConfigField("boolean", "ARRIVAL_DEMO", "false")
            optimization {
                enable = false
            }
        }
    }
    buildTypes.create("demo") {
        initWith(buildTypes.getByName("debug"))
        matchingFallbacks += "debug"
        applicationIdSuffix = ".demo"
        versionNameSuffix = "-demo"
        buildConfigField("boolean", "ARRIVAL_DEMO", "true")
    }
    buildFeatures {
        buildConfig = true
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

dependencies {
    implementation(libs.pleos.navi)
    implementation(libs.pleos.vehicle)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.hilt.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.room.runtime)
    implementation(libs.dagger.hilt.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.retrofit.core)
    implementation(libs.retrofit.converter.kotlinx.serialization)
    implementation(libs.sceneview)
    ksp(libs.androidx.room.compiler)
    ksp(libs.dagger.hilt.compiler)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    "demoImplementation"(libs.androidx.compose.ui.test.manifest)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
}

ksp {
    arg(
        "room.schemaLocation",
        layout.projectDirectory
            .dir("schemas")
            .asFile.absolutePath,
    )
}
