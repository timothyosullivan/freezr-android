// Top-level build file
plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false
    id("com.google.devtools.ksp") version "1.9.22-1.0.17" apply false
}

buildscript {
    repositories { google(); mavenCentral() }
    dependencies { classpath("com.google.dagger:hilt-android-gradle-plugin:2.51") }
}
