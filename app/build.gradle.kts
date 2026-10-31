import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// 发布签名：口令只从本机的 secrets/keystore.properties 读取（该目录已被 git 忽略）。
// 没有这个文件时发布版保持未签名，其他人 clone 下来仍能正常构建调试版。
// 也可以用 -PkeystoreProperties=<文件路径> 指定别的文件。模板见根目录的 keystore.properties.example。
val keystoreProperties = Properties().apply {
    val path = providers.gradleProperty("keystoreProperties").orNull
        ?: rootProject.file("secrets/keystore.properties").path
    val file = file(path)
    if (file.exists()) file.inputStream().use { load(it) }
}

android {
    namespace = "app.pengwa"
    // AndroidX 最新版要求 compileSdk >= 37；targetSdk 仍保持 36，不影响运行时行为
    compileSdk = 37

    defaultConfig {
        applicationId = "app.pengwa"
        // 29：往自己创建的相册条目写照片不需要存储权限，省掉旧版本的一整套分支
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    signingConfigs {
        if (keystoreProperties.getProperty("storeFile") != null) {
            create("release") {
                // 相对路径按项目根目录解析，绝对路径原样使用
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                // 留空表示与库口令相同
                keyPassword = keystoreProperties.getProperty("keyPassword")?.takeIf { it.isNotBlank() }
                    ?: keystoreProperties.getProperty("storePassword")
            }
        }
    }

    buildTypes {
        debug {
            // 调试版可与正式版共存
            applicationIdSuffix = ".debug"
        }
        release {
            signingConfigs.findByName("release")?.let { signingConfig = it }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.kotlin.test.junit)
}
