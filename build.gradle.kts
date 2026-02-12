plugins {
    id("java")
}

group = "com.iisaka"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    implementation("org.neo4j:cypher-v25-antlr-parser:2025.12.1")
    implementation("org.antlr:antlr4-runtime:4.13.2")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.2")
    implementation("org.yaml:snakeyaml:2.2")
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}
