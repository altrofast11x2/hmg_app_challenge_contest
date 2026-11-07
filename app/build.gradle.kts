import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
}

// 서버 주소와 공개(anon) 키는 local.properties에서 읽어 빌드에 넣는다. 저장소에는 올리지 않는다 (local.properties.example 참고)
val localProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

// release 서명 정보는 keystore.properties에서 읽는다 (커밋 금지, keystore.properties.example 참고). 없으면 서명하지 않은 release가 된다
val keystoreProperties = Properties().apply {
    rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
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
        // 앱에 영향을 주는 변경을 배포·제출할 때마다 올린다 (Pleos 앱 버전 관리 정책, docs/app-market.md)
        versionCode = 5
        versionName = "0.4.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // 서버 주소·공개 키는 문자열 리소스로 넣고 실행할 때 읽는다.
        // BuildConfig 상수로 넣으면 Kotlin이 값을 코드에 복사(인라인)해서, local.properties를 바꾼 뒤 증분 빌드하면 옛 값이 남았다
        resValue("string", "supabase_url", localProperties.getProperty("supabase.url", ""))
        resValue("string", "supabase_anon_key", localProperties.getProperty("supabase.anonKey", ""))
        // 휴대폰으로 받기 QR 코드가 여는 관리자 웹 주소 (공개 주소라 기본값을 둔다)
        resValue("string", "web_url", localProperties.getProperty("drivelog.webUrl", "https://altrostudio-drivelog.vercel.app"))
    }

    signingConfigs {
        if (keystoreProperties.getProperty("storeFile") != null) {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // 시험용: ./gradlew assembleDebug -Pdrivelog.debugSuffix=.probe 로 빌드하면 패키지 이름이 달라져
            // 에뮬레이터의 시연용 release 앱(기록 포함)을 지우지 않고 함께 설치할 수 있다
            (project.findProperty("drivelog.debugSuffix") as String?)?.takeIf { it.isNotBlank() }?.let { applicationIdSuffix = it }
        }
        release {
            signingConfig = signingConfigs.findByName("release")
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
    implementation(libs.pleos.texttospeech)
    implementation(libs.pleos.speechtotext)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.zxing.core)
    // 운행 기록 DB 암호화 (Pleos 앱 보안 가이드)
    implementation(libs.sqlcipher.android)
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
    testImplementation(libs.okhttp.mockwebserver)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
