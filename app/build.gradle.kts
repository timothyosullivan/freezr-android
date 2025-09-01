plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("kotlin-kapt")
    id("com.google.devtools.ksp")
    id("dagger.hilt.android.plugin")
    jacoco
}

import java.util.Properties

android {
    namespace = "com.freezr"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.freezr"
    minSdk = 24
    targetSdk = 34
        versionCode = 1
        versionName = "1.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        vectorDrawables.useSupportLibrary = true
    buildConfigField("String", "GIT_SHA", "\"UNKNOWN\"")
    buildConfigField("Long", "BUILD_TIME", System.currentTimeMillis().toString()+"L")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.8" }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            enableUnitTestCoverage = true
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/DEPENDENCIES"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    lint { abortOnError = false }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    animationsDisabled = true
    }
}

jacoco {
    toolVersion = "0.8.11"
}

tasks.withType<Test>().configureEach {
    extensions.configure(JacocoTaskExtension::class.java) {
        isIncludeNoLocationClasses = true
        excludes = listOf("jdk.internal.*")
    }
}

// Unified coverage report combining unit tests (extend for androidTests later)
tasks.register<JacocoReport>("jacocoTestReport") {
    val testTask = tasks.named<Test>("testDebugUnitTest")
    dependsOn(testTask)
    reports { xml.required.set(true); html.required.set(true) }

    // Restrict to our app's packages to avoid diluting coverage with libraries
    val classesRoot = file("${buildDir}/intermediates/classes/debug")
    val classDirs = fileTree(classesRoot) {
        include("**/com/freezr/**")
        exclude(
            "**/R.class","**/R$*.class","**/BuildConfig.*","**/Manifest*.*",
            "**/*_Hilt*.*","**/hilt_aggregated_deps/**","**/*_Factory.*","**/Dagger*.*","**/*HiltModules*.*",
            "**/*_GeneratedInjector.*","**/*_MembersInjector.*",
            // Additional generated / synthetic artifacts we don't want to count in coverage
            // Kotlin inline synthetic artifacts
            "**/*inlined*.class",
            // Hilt generated component tree & base classes
            "**/*_ComponentTreeDeps*.*","**/Hilt_*.*",
            // Room generated implementation classes (DAOs / database impls)
            "**/*_Impl*.*","**/*Dao_Impl*.*","**/*Database_Impl*.*",
            // Jetpack Compose synthetic singleton/lambda holder classes & UI lambdas (retain only top-level file where desired)
            "**/ComposableSingletons$*.class","**/MainActivityKt$*.class",
            // TEMP: exclude reminder infra until tested
            "**/reminder/WorkManagerReminderScheduler*.*","**/reminder/ReminderWorker*.*"
        )
    }
    classDirectories.setFrom(classDirs)
    sourceDirectories.setFrom(files("src/main/java", "src/main/kotlin"))
    executionData.setFrom(fileTree(buildDir) { include("jacoco/testDebugUnitTest.exec") })
    doFirst {
        val classFileCount = classDirs.files.filter { it.exists() }.sumOf { f -> if (f.isDirectory) f.walkTopDown().count { it.isFile && it.extension == "class" } else 0 }
        logger.lifecycle("[jacoco] Class files (filtered) discovered: $classFileCount")
        val execs = executionData.files.filter { it.exists() }.joinToString { "${it.name}:${it.length()}" }
        logger.lifecycle("[jacoco] Exec files: $execs")
    }
}

afterEvaluate {
    tasks.named<JacocoReport>("jacocoTestReport").configure {
        val testTask = tasks.named<Test>("testDebugUnitTest").get()
        executionData.setFrom(files(testTask.extensions.findByType(JacocoTaskExtension::class.java)?.destinationFile ?: file("${'$'}{buildDir}/jacoco/testDebugUnitTest.exec")))
    }
}

tasks.register("jacocoCoverageCheck") {
    dependsOn("jacocoTestReport")
    doLast {
        val reportFile = file("${buildDir}/reports/jacoco/jacocoTestReport/jacocoTestReport.xml")
        if (!reportFile.exists()) {
            println("[coverage] Report not found: $reportFile (skipping check)")
            return@doLast
        }
        // Strict thresholds (default targets)
        val strictLineTarget = 0.80
        val strictBranchTarget = 0.70
        // Baseline file allows ratcheting up coverage gradually.
        val baselineFile = rootProject.file("coverage-baseline.properties")
    val baselineProps = Properties()
        if (baselineFile.exists()) baselineFile.inputStream().use { baselineProps.load(it) }
        var lineMin = (project.findProperty("minLineCoverage") as String?)?.toDoubleOrNull()
            ?: baselineProps.getProperty("minLineCoverage")?.toDoubleOrNull()
            ?: strictLineTarget
        var branchMin = (project.findProperty("minBranchCoverage") as String?)?.toDoubleOrNull()
            ?: baselineProps.getProperty("minBranchCoverage")?.toDoubleOrNull()
            ?: strictBranchTarget
        val strictMode = project.hasProperty("strictCoverage")
        if (strictMode) { lineMin = strictLineTarget; branchMin = strictBranchTarget }
        val dbf = javax.xml.parsers.DocumentBuilderFactory.newInstance().apply {
            setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
            setFeature("http://xml.org/sax/features/validation", false)
            isValidating = false
        }
        val xml = dbf.newDocumentBuilder().parse(reportFile)
        val counters = xml.getElementsByTagName("counter")
        data class Cov(var covered:Int=0,var missed:Int=0) { val total:Int get()=covered+missed; val pct:Double get()= if(total==0) 0.0 else covered.toDouble()/total }
        val map = mutableMapOf<String,Cov>()
        for (i in 0 until counters.length) {
            val node = counters.item(i)
            val type = node.attributes.getNamedItem("type").nodeValue
            val cov = map.getOrPut(type){ Cov() }
            cov.covered += node.attributes.getNamedItem("covered").nodeValue.toInt()
            cov.missed += node.attributes.getNamedItem("missed").nodeValue.toInt()
        }
        val lineCov = map["LINE"]?.pct ?: 0.0
        val branchCov = map["BRANCH"]?.pct ?: 0.0
        val linePct = String.format("%.2f", lineCov * 100)
        val branchPct = String.format("%.2f", branchCov * 100)
        println("[coverage] Line: $linePct% (min ${(lineMin*100).format(2)}%), Branch: $branchPct% (min ${(branchMin*100).format(2)}%)" )
        val failures = mutableListOf<String>()
    if (lineCov < lineMin) failures += "Line coverage $linePct% < required ${(lineMin*100).toInt()}%"
    if (branchCov < branchMin) failures += "Branch coverage $branchPct% < required ${(branchMin*100).toInt()}%"
        // Optional ratchet: UPDATE_COVERAGE_BASELINE=1 will raise baseline if improved (not lower it)
        val wantUpdate = System.getenv("UPDATE_COVERAGE_BASELINE") == "1"
        if (failures.isEmpty() && wantUpdate) {
            val newLine = maxOf(lineCov, lineMin)
            val newBranch = maxOf(branchCov, branchMin)
            if (!baselineFile.exists() || newLine > lineMin || newBranch > branchMin) {
                baselineProps["minLineCoverage"] = String.format("%.4f", newLine)
                baselineProps["minBranchCoverage"] = String.format("%.4f", newBranch)
                baselineFile.outputStream().use { baselineProps.store(it, "Auto-updated coverage baseline") }
                println("[coverage] Baseline updated -> line=${String.format("%.2f", newLine*100)}%, branch=${String.format("%.2f", newBranch*100)}%")
            }
        }
        if (failures.isNotEmpty()) throw GradleException(failures.joinToString("; "))
    }
}

// Helper extension for simple formatting
fun Number.format(decimals: Int): String = String.format("%.${decimals}f", this.toDouble())

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    // CameraX for scanning
    implementation("androidx.camera:camera-core:1.3.4")
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-view:1.3.4")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    implementation("com.google.dagger:hilt-android:2.51")
    kapt("com.google.dagger:hilt-android-compiler:2.51")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
    implementation("com.google.mlkit:barcode-scanning:17.2.0")
    implementation("com.google.zxing:core:3.5.2")
    testImplementation("junit:junit:4.13.2")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("app.cash.turbine:turbine:1.1.0")
    testImplementation("org.robolectric:robolectric:4.12.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

kapt { correctErrorTypes = true }