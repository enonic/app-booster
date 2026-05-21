plugins {
    java
    jacoco
    `maven-publish`
    id("com.enonic.defaults") version "2.1.6"
    id("com.enonic.xp.app")
    id("com.github.node-gradle.node") version "7.1.0"
}

repositories {
    mavenCentral()
    xp.enonicRepo("dev")
}

dependencies {
    implementation(xplibs.api.core)
    implementation(xplibs.api.portal)

    include(libs.brotli4j.brotli4j)
    include(libs.brotli4j.native.windows.amd64)
    include(libs.brotli4j.native.linux.amd64)
    include(libs.brotli4j.native.linux.aarch64)
    include(libs.brotli4j.native.osx.aarch64)
    include(libs.brotli4j.native.osx.amd64)
    include(xplibs.auth)
    include(xplibs.cluster)
    include(xplibs.content)
    include(xplibs.context)
    include(xplibs.io)
    include(xplibs.node)
    include(xplibs.project)
    include(xplibs.portal)
    include(xplibs.task)
    include(xplibs.auditlog)
    include(libs.lib.mustache)
    include(libs.lib.license)
    include(libs.lib.static)
    include(libs.lib.router)

    testImplementation(platform(libs.junit.bom))
    testImplementation(platform(libs.mockito.bom))

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit.jupiter)
    testImplementation(libs.assertj.core)
}

tasks.test {
    useJUnitPlatform()
}

tasks.jacocoTestReport {
    reports {
        xml.required = true
        html.required = true
    }
}

tasks.check {
    dependsOn(tasks.jacocoTestReport)
}

node {
    download = true
    version = "20.18.0"
}

val widgetBuild = tasks.register<com.github.gradle.node.npm.task.NpmTask>("widgetBuild") {
    description = "Builds the React widget bundle via tsup."
    group = "build"
    dependsOn(tasks.named("npmInstall"))
    args = listOf("run", "build")
    inputs.dir("src/main/widget")
    inputs.files("package.json", "package-lock.json", "tsconfig.json", "tsup.config.ts")
    outputs.dir(layout.buildDirectory.dir("widget"))
    environment.set(mapOf("NODE_ENV" to "production"))
}

sourceSets {
    main {
        resources {
            srcDir(widgetBuild)
        }
    }
}
