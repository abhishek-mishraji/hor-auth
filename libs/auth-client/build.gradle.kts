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
	testImplementation("org.junit.jupiter:junit-jupiter:5.13.4")
	testImplementation("org.assertj:assertj-core:3.27.4")
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
