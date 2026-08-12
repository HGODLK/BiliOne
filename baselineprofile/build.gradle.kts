plugins {
  id("com.android.test")
  id("androidx.baselineprofile")
}

android {
  namespace = "dev.openbili.webdemo.baselineprofile"
  compileSdk = 37

  defaultConfig {
    minSdk = 24
    targetSdk = 37
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    // The producer task must generate the Baseline Profile itself. Startup Profile is run
    // explicitly as a separate connected test so the two captured artifacts stay distinct.
    testInstrumentationRunnerArguments["class"] =
      "dev.openbili.webdemo.baselineprofile.BaselineProfileGenerator"
  }

  // Target the app module; the profile is produced against a release-optimized, non-minified
  // benchmark variant of :app that the plugin wires up automatically.
  targetProjectPath = ":app"
  experimentalProperties["android.experimental.self-instrumenting"] = true

}

baselineProfile {
  // Collect on the isolated emulator ADB server (5038); the user's physical device remains on
  // the normal ADB server and is not visible to this Gradle invocation.
  useConnectedDevices = true
}

dependencies {
  implementation("androidx.test.ext:junit:1.3.0")
  implementation("androidx.test.uiautomator:uiautomator:2.4.0")
  implementation("androidx.benchmark:benchmark-macro-junit4:1.5.0-beta01")
}
