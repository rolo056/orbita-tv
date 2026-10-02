plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.orbita.tv"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.orbita.tv"
        minSdk = 21
        targetSdk = 34
        // El numero de compilacion es el numero de version. Asi es monotono sin
        // que nadie lo toque a mano, y la app puede decidir si hay algo nuevo
        // comparando dos enteros. En local queda en 1.
        val build = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toIntOrNull() ?: 1
        versionCode = build
        versionName = "1.0.$build"
    }

    // La clave de firma TIENE que ser la misma en todas las compilaciones.
    // Con la clave de depuracion no lo era: en CI se genera una nueva en cada
    // ejecucion, asi que Android rechazaba instalar encima con "no se instalo la
    // aplicacion" y el actualizador de la app nunca podia aplicar nada.
    val claveEstable = rootProject.file("clave-firma.jks")
    signingConfigs {
        if (claveEstable.exists()) {
            create("estable") {
                storeFile = claveEstable
                storePassword = "alextv"
                keyAlias = "alextv"
                keyPassword = "alextv"
            }
        }
    }

    buildTypes {
        // La variante de prueba se instala AL LADO de la app de siempre, como
        // otra app ("ALEX TV prueba"): probar algo nuevo no obliga a
        // desinstalar la que funciona ni a volver a cargarle la cuenta. Va con
        // la misma firma estable, para que una prueba nueva se instale encima
        // de la prueba anterior.
        debug {
            applicationIdSuffix = ".prueba"
            versionNameSuffix = " de prueba"
            if (claveEstable.exists()) {
                signingConfig = signingConfigs.getByName("estable")
            }
        }
        release {
            isMinifyEnabled = false
            // No pasa por Play Store: se instala a mano. La contrasena esta a la
            // vista a proposito, no protege nada; lo que importa es que no
            // cambie entre compilaciones.
            signingConfig = if (claveEstable.exists()) {
                signingConfigs.getByName("estable")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf("-opt-in=androidx.media3.common.util.UnstableApi",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi")
    }

    buildFeatures {
        compose = true
    }

    // Las "pruebas" de este proyecto dibujan cada pantalla a un PNG, sin emulador.
    // Existen porque aqui no hay forma de ver la app antes de instalarla en el
    // televisor, y los fallos de pantalla solo aparecian en las fotos del usuario.
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                it.maxHeapSize = "3g"
                it.systemProperty("roborazzi.test.record", "true")
                it.testLogging {
                    events("passed", "failed", "skipped")
                    exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
                }
            }
        }
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")

    val media3 = "1.5.1"
    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-exoplayer-hls:$media3")
    implementation("androidx.media3:media3-ui:$media3")
    implementation("androidx.media3:media3-datasource-okhttp:$media3")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:okhttp-dnsoverhttps:4.12.0")

    implementation("io.coil-kt:coil-compose:2.7.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.17")
    testImplementation("androidx.test:core:1.7.0")
    testImplementation("androidx.test.ext:junit:1.3.0")
    // 1.60.0 es la ultima legible por Kotlin 2.0: desde la 1.61.0 la libreria se
    // compila con Kotlin 2.3 y este proyecto no puede leer sus metadatos. Subirla
    // exige subir antes el Kotlin de toda la app.
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.60.0")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.60.0")
}
