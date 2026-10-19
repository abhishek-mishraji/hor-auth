pluginManagement {
	plugins {
		id("org.springframework.boot") version providers.gradleProperty("springBootVersion").get()
		id("io.spring.dependency-management") version providers.gradleProperty("dependencyManagementVersion").get()
	}
}

rootProject.name = "auth-service"
includeBuild("../../libs/auth-client")
