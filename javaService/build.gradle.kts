plugins {
	id("java")
	id("org.springframework.boot") version "4.1.2"
	id("io.spring.dependency-management") version "1.1.7"
	id("io.freefair.lombok") version "8.10"
}

group = "com.example"
version = "0.0.1-SNAPSHOT"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

repositories {
	mavenCentral()
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter")
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.4.0")
	implementation("org.springframework.boot:spring-boot-starter-web")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.kafka:spring-kafka")
	implementation("org.springframework.boot:spring-boot-starter-flyway")
	implementation("org.springframework.boot:spring-boot-starter-jackson")
	implementation("io.jsonwebtoken:jjwt-api:0.12.6")
	runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
	runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")
	runtimeOnly("org.flywaydb:flyway-database-postgresql")
	runtimeOnly("org.postgresql:postgresql")
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
	testImplementation("com.h2database:h2")
	testImplementation("org.springframework.kafka:spring-kafka-test")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")

}

tasks.withType<JavaCompile> {
    options.compilerArgs.addAll(listOf("-Xlint:deprecation"))
}

tasks.withType<Test> {
	useJUnitPlatform()
}

// Шаг 10: обычный test не должен требовать ключ и ходить во внешний API,
// поэтому тег llm-eval из него исключён, а отдельная задача llmEval
// выполняет только эти тесты.
tasks.named<Test>("test") {
	useJUnitPlatform {
		excludeTags("llm-eval")
	}
}

tasks.register<Test>("llmEval") {
	group = "verification"
	description = "Прогон датасета русских фраз через LLM-модели (нужен OPENROUTER_API_KEY)."
	useJUnitPlatform {
		includeTags("llm-eval")
	}
	testClassesDirs = sourceSets["test"].output.classesDirs
	classpath = sourceSets["test"].runtimeClasspath
	shouldRunAfter(tasks.named("test"))
	doFirst {
		if (System.getenv("OPENROUTER_API_KEY").isNullOrBlank()) {
			println("ПРЕДУПРЕЖДЕНИЕ: OPENROUTER_API_KEY не задан — llmEval будет пропущен (тест отключён через @EnabledIfEnvironmentVariable).")
		}
	}
}

// Docker copies build/libs/*.jar to app.jar, and a glob matching two files
// fails. Keep only the executable boot jar and drop the plain one.
tasks.named("jar") {
    enabled = false
}
