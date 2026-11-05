plugins {
    id("com.android.application")
}

android {
    namespace = "kkam.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "kkam.app"
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "0.1"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // 시나리오는 엔진 테스트와 같은 파일을 쓴다. 복사하지 않는다.
    sourceSets.getByName("main").assets.directories.add(rootProject.file("scenarios").path)
}

dependencies {
    implementation(project(":engine"))
    implementation("ai.pleos.playground:TextToSpeech:3.3.1")

    testImplementation(kotlin("test-junit5"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
