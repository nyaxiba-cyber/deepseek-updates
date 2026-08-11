plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.deepseek.personal"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.deepseek.personal"
        minSdk = 26
        targetSdk = 34
        versionCode = 21
        versionName = "1.20"

        // API Key 不内置进 APK：首次打开后由用户在设置页填写，
        // 保存在 App 本地配置文件（DataStore），更新安装时不会被删除。
        // 更新清单：GitHub raw（实时无缓存）；APK 下载走 jsDelivr CDN（国内加速）
        // 更新清单：腾讯云 COS（国内直连、自带 HTTPS，无需备案/梯子）
        val updateUrl = (project.findProperty("UPDATE_URL") as String?)
            ?: "https://deepseek-update-1320021760.cos.ap-chengdu.myqcloud.com/version.json"
        buildConfigField("String", "UPDATE_URL_DEFAULT", "\"$updateUrl\"")

        // 蒲公英检查更新：注册 pgyer.com 后在「账户设置 -> API 信息」拿 API Key，
        // 应用 Key 在「应用管理 -> 应用 -> 安装设置」拿
        val pgyerApiKey = (project.findProperty("PGYER_API_KEY") as String?) ?: ""
        val pgyerAppKey = (project.findProperty("PGYER_APP_KEY") as String?) ?: ""
        buildConfigField("String", "PGYER_API_KEY", "\"$pgyerApiKey\"")
        buildConfigField("String", "PGYER_APP_KEY", "\"$pgyerAppKey\"")
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
        buildConfig = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.03")

    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.navigation:navigation-compose:2.8.2")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:okhttp-sse:4.12.0")
    implementation("com.halilibo.compose-richtext:richtext-commonmark:0.20.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("io.coil-kt:coil-compose:2.7.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
