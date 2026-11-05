plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    // 앱이 시나리오 JSON을 읽을 때도 쓰므로 api로 공개한다.
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.12.0")

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()

    // 시나리오 폴더 위치를 테스트에 알려주고, JSON이 바뀌면 테스트를 다시 실행한다.
    val scenariosDir = rootProject.file("scenarios")
    systemProperty("scenarios.dir", scenariosDir.path)
    inputs.dir(scenariosDir)
}
