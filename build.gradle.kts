plugins {
    java
    jacoco
    `maven-publish`
    id("com.enonic.defaults") version "2.1.6"
    id("com.enonic.xp.app")
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
    include(libs.lib.mustache)
    include(libs.lib.license)

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
