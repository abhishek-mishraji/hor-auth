plugins {
	`java-library`
	`maven-publish`
}

group = "com.handsoff.hor"
version = "0.1.0"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
	withSourcesJar()
}

repositories {
	mavenCentral()
}

dependencies {
	api("org.springframework.boot:spring-boot-autoconfigure:4.1.1")
	api("org.springframework.boot:spring-boot-starter-oauth2-resource-server:4.1.1")
	api("org.springframework.boot:spring-boot-starter-webmvc:4.1.1")
	testImplementation("org.springframework.boot:spring-boot-starter-test:4.1.1")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile>().configureEach {
	options.compilerArgs.addAll(listOf("-parameters", "-Xlint:all,-processing,-serial"))
}

tasks.withType<Test>().configureEach {
	useJUnitPlatform()
	maxParallelForks = 1
}

publishing {
	publications {
		create<MavenPublication>("mavenJava") {
			from(components["java"])
			artifactId = "auth-client"
		}
	}
}
