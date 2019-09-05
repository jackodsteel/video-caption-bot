import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
	id("de.fayard.buildSrcVersions") version "0.4.2"
	id("org.springframework.boot") version "2.1.7.RELEASE"
	kotlin("jvm") version "1.3.50"
	kotlin("plugin.spring") version "1.3.50"
	id("io.spring.dependency-management") version "1.0.8.RELEASE"
	id("org.jetbrains.kotlin.plugin.serialization") version "1.3.50"
}

group = "nz.co.jacksteel"
version = "0.0.1-SNAPSHOT"

repositories {
	mavenCentral()
	jcenter()
	maven(url = "https://kotlin.bintray.com/ktor")
	maven(url = "https://kotlin.bintray.com/kotlinx")
	maven(url = "https://kotlin.bintray.com/kotlin-eap")
	maven(url = "https://dl.bintray.com/nephyproject/stable")
}

dependencies {
	val kotlinVersion = "1.3.50"
	val kotlinCoroutinesVersion = "1.3.0"
	val springBootVersion = "2.1.7.RELEASE"
	val jacksonVersion = "2.9.9"
	val penicillinVersion = "4.2.3"
	val jsonKtVersion = "4.10"
	val ktorVersion = "1.2.3"
	val ffmpegVersion = "0.6.2"

	implementation("io.ktor:ktor-client-json:$ktorVersion")
	implementation("io.ktor:ktor-client-gson:$ktorVersion")
	implementation("jp.nephy:jsonkt:$jsonKtVersion")
	implementation("org.springframework.boot:spring-boot-starter-web:$springBootVersion")
	implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")
	implementation(kotlin(module ="stdlib-jdk8", version = kotlinVersion))
	implementation(kotlin(module = "reflect", version = kotlinVersion))
	implementation("jp.nephy:penicillin:$penicillinVersion")
	implementation("io.ktor:ktor-client-apache:$ktorVersion")
	implementation("net.bramp.ffmpeg:ffmpeg:$ffmpegVersion")
	implementation("org.jetbrains.kotlinx:kotlinx-serialization-runtime:0.12.0")
	implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$kotlinCoroutinesVersion")
	implementation("org.springframework.boot:spring-boot-starter-webflux:$springBootVersion")
	testImplementation("org.springframework.boot:spring-boot-starter-test:$springBootVersion")
}

tasks.withType<KotlinCompile> {
	kotlinOptions {
		freeCompilerArgs = listOf("-Xjsr305=strict")
		jvmTarget = "1.8"
	}
}