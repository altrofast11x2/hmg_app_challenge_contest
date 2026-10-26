plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.altrostudio.drivelog"
    compileSdk = 35
    // AAOS Car API (기어, 주차 브레이크, 주행 제한). 차량 OS에 내장되어 있어 APK에는 포함되지 않는다
    useLibrary("android.car")

    defaultConfig {
        applicationId = "com.altrostudio.drivelog"
        // Pleos 요구사항: API 28 이상 (차량 보안 요건)
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    testOptions {
        // 단위 테스트에서 android.util.Log 호출이 실패하지 않도록 기본값을 돌려준다
        unitTests.isReturnDefaultValues = true
    }
}

ksp {
    // DB 구조를 파일로 남겨 버전이 바뀔 때 마이그레이션을 검증한다
    arg("room.schemaLocation", "$projectDir/schemas")
}

androidComponents {
    // Fake 구현은 debug 소스에만 있으므로 단위 테스트는 debug 빌드로만 돌린다
    beforeVariants(selector().withBuildType("release")) { it.enableUnitTest = false }
}

dependencies {

    implementation(libs.pleos.vehicle)
    implementation(libs.pleos.navihelper)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
