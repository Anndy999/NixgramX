plugins {
    `kotlin-dsl`
}

gradlePlugin {
    plugins {
        register("lottiePreParser") {
            id = "org.telegram.lottie-meta"
            implementationClass = "org.telegram.lottie.LottieMetaPlugin"
        }
    }
}

repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        languageVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_1_9)
        apiVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_1_9)
    }
    incremental = false
}

dependencies {
    implementation(gradleApi())
    // Match root AGP (NixgramX uses 9.3.1; Telegram upstream still pins 8.x)
    implementation("com.android.tools.build:gradle:9.3.1")
    implementation("com.google.code.gson:gson:2.11.0")
}
