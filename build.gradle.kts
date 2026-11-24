import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    id("dev.mantikafasi.manticord.plugin")
}

val releaseSigningFile = rootProject.file("keystore.properties")
val releaseSigning = Properties().apply {
    if (releaseSigningFile.exists()) releaseSigningFile.inputStream().use(::load)
}

manticordPlugin {
    id.set("manticord.orbs_shop")
    entrypoint.set("io.github.mmaarij123.orbsshop.OrbsShopPlugin")
    description.set("Browse and redeem Discord cosmetics with Orbs")
    manticordVersionMinimum.set(1)
    manticordVersionMaximumExclusive.set(2)
    usesResources.set(true)
    async()
}

android {
    namespace = "io.github.mmaarij123.orbsshop"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "io.github.mmaarij123.orbsshop"
        minSdk = 23
        targetSdk = 36
        versionCode = 5
        versionName = "1.2.2"
    }

    signingConfigs {
        if (releaseSigningFile.exists()) create("release") {
            storeFile = rootProject.file(releaseSigning.getProperty("storeFile"))
            storePassword = releaseSigning.getProperty("storePassword")
            keyAlias = releaseSigning.getProperty("keyAlias")
            keyPassword = releaseSigning.getProperty("keyPassword")
        }
    }

    buildTypes {
        getByName("release") {
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

}

dependencies {
    val localPluginApi = file("../manticordrebuild/build/plugin-sdk/manticord-plugin-api-1.0.6.jar")
    compileOnly(
        if (localPluginApi.exists()) files(localPluginApi)
        else "dev.mantikafasi.manticord:plugin-api:1.0.6"
    )
    compileOnly(libs.rxjava)
    compileOnly("com.squareup.retrofit2:retrofit:3.0.0")
    compileOnly("com.google.code.gson:gson:2.14.0")
    compileOnly("androidx.appcompat:appcompat:1.8.0")
    compileOnly("androidx.fragment:fragment:1.8.9")
    compileOnly("com.linecorp:apng:1.12.0")
    testImplementation("com.google.code.gson:gson:2.14.0")
    testImplementation("junit:junit:4.13.2")
}
