import java.security.MessageDigest
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Segredos: lidos de secrets.properties (no PC) ou de variáveis de ambiente (GitHub Actions).
// Nunca coloque senhas diretamente neste arquivo.
val secrets = Properties().apply {
    val f = rootProject.file("secrets.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

fun secret(key: String, default: String = ""): String =
    secrets.getProperty(key)?.takeIf { it.isNotBlank() }
        ?: System.getenv(key)?.takeIf { it.isNotBlank() }
        ?: default

fun quoted(v: String) = "\"" + v.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

fun sha256(v: String): String =
    if (v.isEmpty()) "" else MessageDigest.getInstance("SHA-256")
        .digest(v.toByteArray()).joinToString("") { "%02x".format(it) }

android {
    namespace = "br.com.hasolucoes.rastreio"
    compileSdk = 34

    defaultConfig {
        applicationId = "br.com.hasolucoes.rastreio"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        buildConfigField("String", "SMTP_HOST", quoted(secret("SMTP_HOST", "smtp.titan.email")))
        buildConfigField("int", "SMTP_PORT", secret("SMTP_PORT", "465"))
        buildConfigField("String", "SMTP_USER", quoted(secret("SMTP_USER")))
        buildConfigField("String", "SMTP_PASSWORD", quoted(secret("SMTP_PASSWORD")))
        buildConfigField("String", "MAIL_FROM", quoted(secret("MAIL_FROM", secret("SMTP_USER"))))
        buildConfigField("String", "MAIL_TO", quoted(secret("MAIL_TO")))
        buildConfigField("String", "DEVICE_LABEL", quoted(secret("DEVICE_LABEL", "Vendedor 01")))
        buildConfigField("String", "ADMIN_PIN_SHA256", quoted(sha256(secret("ADMIN_PIN"))))
    }

    buildFeatures {
        buildConfig = true
    }

    signingConfigs {
        create("release") {
            storeFile = rootProject.file(secret("KEYSTORE_FILE", "rastreio.jks"))
            storePassword = secret("KEYSTORE_PASSWORD")
            keyAlias = secret("KEY_ALIAS", "rastreio")
            keyPassword = secret("KEY_PASSWORD", secret("KEYSTORE_PASSWORD"))
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/LICENSE.md",
                "META-INF/NOTICE.md",
                "META-INF/LICENSE",
                "META-INF/NOTICE",
                "META-INF/DEPENDENCIES"
            )
        }
    }
}

dependencies {
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("com.sun.mail:android-mail:1.6.7")
    implementation("com.sun.mail:android-activation:1.6.7")
}
