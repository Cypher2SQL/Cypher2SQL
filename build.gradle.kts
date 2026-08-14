plugins {
    id("java")
}

val integrationTest by sourceSets.creating {
    java.srcDir("src/integrationTest/java")
    resources.srcDir("src/test/resources")
    compileClasspath += sourceSets.main.get().output + sourceSets.main.get().compileClasspath
    runtimeClasspath += output + compileClasspath + sourceSets.main.get().runtimeClasspath
}

group = "com.iisaka"
version = "0.1.0"

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
    add("integrationTestImplementation", platform("org.junit:junit-bom:5.10.0"))
    add("integrationTestImplementation", "org.junit.jupiter:junit-jupiter")
    add("integrationTestImplementation", "org.xerial:sqlite-jdbc:3.46.1.0")
    add("integrationTestRuntimeOnly", "org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

val integrationTestTask = tasks.register<Test>("integrationTest") {
    description = "Runs integration tests."
    group = "verification"
    testClassesDirs = integrationTest.output.classesDirs
    classpath = integrationTest.runtimeClasspath
    shouldRunAfter(tasks.test)
    useJUnitPlatform()
}

tasks.check {
    dependsOn(integrationTestTask)
}
