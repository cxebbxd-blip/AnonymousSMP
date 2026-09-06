plugins {
    java
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.22"
}
group = "dev.cxebby"
version = "1.0.2"
repositories { mavenCentral() }
dependencies {
    paperweight.paperDevBundle("1.21.11-R0.1-SNAPSHOT")
    testImplementation(platform("org.junit:junit-bom:5.12.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.mockito:mockito-core:5.17.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
java { toolchain.languageVersion.set(JavaLanguageVersion.of(21)) }
paperweight.reobfArtifactConfiguration = io.papermc.paperweight.userdev.ReobfArtifactConfiguration.MOJANG_PRODUCTION
tasks.withType<JavaCompile>().configureEach { options.encoding = "UTF-8"; options.release.set(21) }
tasks.test { useJUnitPlatform() }
tasks.processResources { filesMatching("plugin.yml") { expand("version" to project.version) } }
tasks.jar {
    archiveFileName.set("AnonymousSMP.jar")
    from("LICENSE", "NOTICE") { into("META-INF") }
    manifest.attributes["paperweight-mappings-namespace"] = "mojang"
}
tasks.register<Copy>("exportDeps") {
    from(sourceSets.main.get().compileClasspath)
    from(configurations.testRuntimeClasspath)
    into(layout.buildDirectory.dir("compile-deps"))
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
