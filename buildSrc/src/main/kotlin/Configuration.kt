@file:Suppress("unused", "SpellCheckingInspection", "ConstPropertyName")

object Android {
    const val compileSdk = 36
    const val minSdk = 30
    const val targetSdk = 35

    const val appName = "FlashLight"
    const val applicationId = "com.name.flashlight"
    const val applicationId_test = "com.name.flashlight"
    const val versionName = "1.0.0"
    const val versionCode = 10000
}

object Options {
    // TODO 正式发布时调用
    const val IS_PUBLISH: Boolean = false

    // TODO ADMOB_ID 正式
    const val ADMOB_ID = "ca-app-pub-9488501426181082~6354662111"

    // ADMOB_ID 测试
    const val ADMOB_ID_TEST = "ca-app-pub-9488501426181082~6354662111"
}

object Versions {
    const val gradle = "8.13.0"
    const val kotlin = "2.2.10"
    const val lifecycle = "2.9.3"
    const val okhttp = "4.12.0"
}

object Deps {
    // Kotlin
    const val kotlin_reflect = "org.jetbrains.kotlin:kotlin-reflect:${Versions.kotlin}"
    const val kotlin_stdlib = "org.jetbrains.kotlin:kotlin-stdlib:${Versions.kotlin}"

    // AndroidX
    const val core_ktx = "androidx.core:core-ktx:1.17.0"
    const val appcompat = "androidx.appcompat:appcompat:1.7.1"
    const val viewpager2 = "androidx.viewpager2:viewpager2:1.1.0"
    const val recyclerview = "androidx.recyclerview:recyclerview:1.4.0"
    const val constraintlayout = "androidx.constraintlayout:constraintlayout:2.2.1"
    const val splashscreen = "androidx.core:core-splashscreen:1.0.1"
    const val swiperefreshlayout = "androidx.swiperefreshlayout:swiperefreshlayout:1.1.0"
    const val material = "com.google.android.material:material:1.13.0"
    const val fragment = "androidx.fragment:fragment:1.8.9"
    const val worker_runtime = "androidx.work:work-runtime-ktx:2.9.0"
    const val datastore_preferences = "androidx.datastore:datastore-preferences:1.0.0"

    // Lifecycle
    const val lifecycle_runtime_ktx =
        "androidx.lifecycle:lifecycle-runtime-ktx:${Versions.lifecycle}"
    const val lifecycle_viewmodel_ktx =
        "androidx.lifecycle:lifecycle-viewmodel-ktx:${Versions.lifecycle}"
    const val lifecycle_livedata_ktx =
        "androidx.lifecycle:lifecycle-livedata-ktx:${Versions.lifecycle}"

    // OkHttp
    const val okhttp = "com.squareup.okhttp3:okhttp:${Versions.okhttp}"
    const val okhttp_logging = "com.squareup.okhttp3:logging-interceptor:${Versions.okhttp}"

    const val gson = "com.google.code.gson:gson:2.11.0"
    const val glide = "com.github.bumptech.glide:glide:4.16.0"

    const val lottie = "com.airbnb.android:lottie:6.6.7"

    const val review = "com.google.android.play:review:2.0.2"
    const val coil = "io.coil-kt:coil:2.7.0"
}