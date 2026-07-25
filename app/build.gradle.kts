import org.gradle.jvm.tasks.Jar
import java.util.Properties

plugins {
  id("com.android.application")
  id("org.jetbrains.kotlin.plugin.compose")
  id("com.diffplug.spotless")
}

val biliAppSignerEndpoint =
  providers
    .gradleProperty("BILI_APP_SIGNER_ENDPOINT")
    .orElse(providers.environmentVariable("BILI_APP_SIGNER_ENDPOINT"))
    .orElse("")

val releaseSigningProperties =
  Properties().apply {
    val propertiesFile = rootProject.file("keystore.properties")
    if (propertiesFile.isFile) {
      propertiesFile.inputStream().use(::load)
    }
  }

spotless {
  kotlin {
    target("src/**/*.kt")
    ktfmt().googleStyle()
  }
  kotlinGradle {
    target("*.gradle.kts")
    ktfmt().googleStyle()
  }
}

android {
  namespace = "dev.openbili.webdemo"
  compileSdk = 37

  defaultConfig {
    applicationId = "io.github.shuyunr.bilione"
    minSdk = 24
    targetSdk = 37
    versionCode = 1
    versionName = "0.1.0-preview.1"
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    vectorDrawables.useSupportLibrary = true
    buildConfigField(
      "String",
      "BILI_APP_SIGNER_ENDPOINT",
      "\"${biliAppSignerEndpoint.get().replace("\\", "\\\\").replace("\"", "\\\"")}\"",
    )
  }

  signingConfigs {
    create("release") {
      storeFile = rootProject.file(releaseSigningProperties.getProperty("storeFile", ""))
      storePassword = releaseSigningProperties.getProperty("storePassword", "")
      keyAlias = releaseSigningProperties.getProperty("keyAlias", "")
      keyPassword = releaseSigningProperties.getProperty("keyPassword", "")
      enableV1Signing = true
      enableV2Signing = true
      enableV3Signing = true
    }
  }

  buildTypes {
    debug { isMinifyEnabled = false }
    release {
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("release")
    }
  }

  compileOptions {
    isCoreLibraryDesugaringEnabled = true
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  packaging.resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
}

dependencies {
  coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.3")
  implementation("androidx.activity:activity-compose:1.13.0")
  implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
  implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
  implementation("androidx.compose.material3:material3:1.4.0")
  implementation("androidx.compose.material:material-icons-core:1.7.8")
  implementation("androidx.compose.animation:animation:1.11.4")
  implementation("androidx.compose.foundation:foundation:1.11.4")
  implementation("androidx.compose.ui:ui:1.11.4")
  implementation("androidx.compose.ui:ui-tooling-preview:1.11.4")
  implementation("androidx.webkit:webkit:1.16.0")
  implementation("androidx.core:core-ktx:1.19.0")
  implementation("io.coil-kt.coil3:coil-compose:3.5.0")
  implementation("io.coil-kt.coil3:coil-gif:3.5.0")
  implementation("io.coil-kt.coil3:coil-svg:3.5.0")
  implementation("com.google.zxing:core:3.5.3")
  implementation("io.coil-kt.coil3:coil-network-okhttp:3.5.0")
  // ExoPlayer (media3)
  implementation("androidx.media3:media3-exoplayer:1.10.1")
  implementation("androidx.media3:media3-exoplayer-dash:1.10.1")
  implementation("androidx.media3:media3-datasource:1.10.1")
  implementation("androidx.media3:media3-database:1.10.1")
  implementation("androidx.media3:media3-ui:1.10.1")

  debugImplementation("androidx.compose.ui:ui-tooling:1.11.4")
  debugImplementation("androidx.compose.ui:ui-test-manifest:1.11.4")
  testImplementation("junit:junit:4.13.2")
  testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
  testImplementation("org.robolectric:robolectric:4.16.1")
  androidTestImplementation("androidx.test.ext:junit:1.3.0")
  androidTestImplementation("androidx.test:core-ktx:1.7.0")
  androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
  androidTestImplementation("androidx.compose.ui:ui-test-junit4:1.11.4")
}

// The JUnit worker cannot load loose class files from this non-ASCII Windows project path.
// Packaging them first avoids that URL-classloader edge case while leaving production untouched.
val debugUnitTestClassesJar =
  tasks.register<Jar>("debugUnitTestClassesJar") {
    dependsOn("compileDebugUnitTestKotlin")
    archiveFileName.set("debug-unit-test-classes.jar")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    destinationDirectory.set(
      layout.dir(
        providers.provider {
          file(System.getProperty("java.io.tmpdir")).resolve("bili-web-demo-test-jars")
        }
      )
    )
    from(
      layout.buildDirectory.dir("intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes")
    )
    from(
      layout.buildDirectory.dir(
        "intermediates/built_in_kotlinc/debugUnitTest/compileDebugUnitTestKotlin/classes"
      )
    )
  }

tasks.withType<Test>().configureEach {
  if (name == "testDebugUnitTest") {
    dependsOn(debugUnitTestClassesJar)
    doFirst {
      classpath = files(debugUnitTestClassesJar.get().archiveFile) + classpath
    }
  }
}
