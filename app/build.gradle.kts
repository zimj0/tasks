import com.android.build.gradle.api.ApplicationVariant

plugins {
    id("com.android.application")
    id("checkstyle")
    id("io.fabric")
    id("com.cookpad.android.licensetools")
    kotlin("android")
}

repositories {
    jcenter()
    google()
}

android {
    lintOptions {
        setLintConfig(file("lint.xml"))
        textOutput("stdout")
        textReport = true
    }

    compileSdkVersion(Versions.compileSdk)

    defaultConfig {
        testApplicationId = "org.tasks.test"
        applicationId = "org.tasks"
        versionCode = 589
        versionName = "6.7"
        targetSdkVersion(Versions.compileSdk)
        minSdkVersion(Versions.minSdk)
        multiDexEnabled = true
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        javaCompileOptions {
            annotationProcessorOptions {
                arguments["room.schemaLocation"] = "$projectDir/schemas"
            }
        }
    }

    signingConfigs {
        create("release") {
            val tasksKeyAlias: String? by project
            val tasksStoreFile: String? by project
            val tasksStorePassword: String? by project
            val tasksKeyPassword: String? by project

            keyAlias = tasksKeyAlias
            storeFile = file(tasksStoreFile?: "none")
            storePassword = tasksStorePassword
            keyPassword = tasksKeyPassword
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    buildTypes {
        getByName("debug") {
            val tasks_mapbox_key_debug: String? by project
            val tasks_google_key_debug: String? by project

            applicationIdSuffix = ".debug"
            resValue("string", "mapbox_key", tasks_mapbox_key_debug ?: "")
            resValue("string", "google_key", tasks_google_key_debug ?: "")
            isTestCoverageEnabled = project.hasProperty("coverage")
        }
        getByName("release") {
            val tasks_mapbox_key: String? by project
            val tasks_google_key: String? by project

            resValue("string", "mapbox_key", tasks_mapbox_key ?: "")
            resValue("string", "google_key", tasks_google_key ?: "")
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android.txt"), "proguard.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }

    applicationVariants.all(object : Action<ApplicationVariant> {
        override fun execute(variant: ApplicationVariant) {
            variant.resValue("string", "app_package", variant.applicationId)
        }
    })

    flavorDimensions("store")

    productFlavors {
        create("generic") {
            setDimension("store")
            proguardFile("generic.pro")
        }
        create("googleplay") {
            setDimension("store")
        }
        create("amazon") {
            setDimension("store")
        }
    }
}

configure<CheckstyleExtension> {
    configFile = project.file("google_checks.xml")
    toolVersion = "8.16"
}

configurations.all {
    exclude(group = "com.google.guava", module = "guava-jdk5")
    exclude(group = "org.apache.httpcomponents", module = "httpclient")
    exclude(group = "com.google.http-client", module = "google-http-client-apache")
}

val googleplayImplementation by configurations
val amazonImplementation by configurations

dependencies {
    implementation(project(":dav4jvm")) {
        exclude(group = "org.ogce", module = "xpp3")
    }
    implementation(project(":ical4android")) {
        exclude(group = "org.threeten", module = "threetenbp")
    }

    annotationProcessor("com.google.dagger:dagger-compiler:${Versions.dagger}")
    implementation("com.google.dagger:dagger:${Versions.dagger}")

    implementation("androidx.room:room-rxjava2:${Versions.room}")
    annotationProcessor("androidx.room:room-compiler:${Versions.room}")
    implementation("androidx.lifecycle:lifecycle-extensions:2.0.0")
    implementation("io.reactivex.rxjava2:rxandroid:2.1.1")
    implementation("androidx.paging:paging-runtime:2.1.0")

    annotationProcessor("com.jakewharton:butterknife-compiler:${Versions.butterknife}")
    implementation("com.jakewharton:butterknife:${Versions.butterknife}")

    debugImplementation("com.facebook.flipper:flipper:0.22.0")
    debugImplementation("com.facebook.soloader:soloader:0.6.0")

    debugImplementation("com.squareup.leakcanary:leakcanary-android:${Versions.leakcanary}")
    debugImplementation("com.squareup.leakcanary:leakcanary-support-fragment:${Versions.leakcanary}")

    implementation("org.jetbrains.kotlin:kotlin-stdlib:${Versions.kotlin}")
    implementation("io.github.luizgrp.sectionedrecyclerviewadapter:sectionedrecyclerviewadapter:2.0.0")
    implementation("androidx.multidex:multidex:2.0.1")
    implementation("me.saket:better-link-movement-method:2.2.0")
    //noinspection GradleDependency
    implementation("com.squareup.okhttp3:okhttp:3.12.2") // 3.13 minSdk is 21
    implementation("com.google.code.gson:gson:2.8.5")
    implementation("com.github.rey5137:material:1.2.5")
    implementation("com.nononsenseapps:filepicker:4.2.1")
    implementation("com.google.android.material:material:1.0.0")
    implementation("androidx.annotation:annotation:1.1.0")
    implementation("androidx.cardview:cardview:1.0.0")
    implementation("androidx.constraintlayout:constraintlayout:2.0.0-beta1")
    implementation("com.jakewharton.timber:timber:4.7.1")
    implementation("com.jakewharton.threetenabp:threetenabp:1.2.0")
    //noinspection GradleDependency
    implementation("com.google.guava:guava:27.1-android")
    implementation("com.jakewharton:process-phoenix:2.0.0")
    implementation("com.google.android.apps.dashclock:dashclock-api:2.0.0")
    implementation("com.twofortyfouram:android-plugin-api-for-locale:1.0.2")
    implementation("com.rubiconproject.oss:jchronic:0.2.6") {
        isTransitive = false
    }
    implementation("org.scala-saddle:google-rfc-2445:20110304") {
        isTransitive = false
    }
    implementation("com.wdullaer:materialdatetimepicker:4.0.1")
    implementation("me.leolin:ShortcutBadger:1.1.22@aar")
    implementation("com.google.apis:google-api-services-tasks:v1-rev59-1.25.0")
    implementation("com.google.apis:google-api-services-drive:v3-rev165-1.25.0")
    implementation("com.google.api-client:google-api-client-android:1.29.2")
    implementation("androidx.work:work-runtime:${Versions.work}")
    implementation("com.mapbox.mapboxsdk:mapbox-android-sdk:7.3.0")
    implementation("com.mapbox.mapboxsdk:mapbox-sdk-services:4.6.0")

    googleplayImplementation("com.crashlytics.sdk.android:crashlytics:${Versions.crashlytics}")
    googleplayImplementation("com.google.firebase:firebase-core:${Versions.firebase}")
    googleplayImplementation("com.google.android.gms:play-services-location:16.0.0")
    googleplayImplementation("com.google.android.gms:play-services-maps:16.1.0")
    googleplayImplementation("com.google.android.libraries.places:places:1.1.0")
    googleplayImplementation("com.android.billingclient:billing:1.2.2")

    amazonImplementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
    amazonImplementation("com.crashlytics.sdk.android:crashlytics:${Versions.crashlytics}")
    amazonImplementation("com.google.firebase:firebase-core:${Versions.firebase}")

    androidTestAnnotationProcessor("com.google.dagger:dagger-compiler:${Versions.dagger}")
    androidTestAnnotationProcessor("com.jakewharton:butterknife-compiler:${Versions.butterknife}")
    androidTestImplementation("com.google.dexmaker:dexmaker-mockito:1.2")
    androidTestImplementation("com.natpryce:make-it-easy:4.0.1")
    androidTestImplementation("androidx.test:runner:1.2.0")
    androidTestImplementation("androidx.test:rules:1.2.0")
    androidTestImplementation("androidx.test.ext:junit:1.1.1")
    androidTestImplementation("androidx.annotation:annotation:1.1.0")
}

apply(mapOf("plugin" to "com.google.gms.google-services"))
