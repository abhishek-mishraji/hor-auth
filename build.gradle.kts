plugins {
	base
}

val authClientBuild = gradle.includedBuild("auth-client")
val authServiceBuild = gradle.includedBuild("auth-service")

tasks.named("build") {
	dependsOn(authClientBuild.task(":build"), authServiceBuild.task(":build"))
}
tasks.named("check") {
	dependsOn(authClientBuild.task(":check"), authServiceBuild.task(":check"))
}
tasks.named("assemble") {
	dependsOn(authClientBuild.task(":assemble"), authServiceBuild.task(":assemble"))
}
tasks.named("clean") {
	dependsOn(authClientBuild.task(":clean"), authServiceBuild.task(":clean"))
}
