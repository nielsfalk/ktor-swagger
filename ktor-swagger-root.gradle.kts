import org.jetbrains.kotlin.gradle.dsl.Coroutines

buildscript {
    repositories {
        mavenCentral()
        jcenter()
        maven {
            setUrl("https://plugins.gradle.org/m2/")
        }
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:${property("kotlin.version")}")
    }
}
plugins {
    // https://github.com/diffplug/spotless/tree/master/plugin-gradle
    id("com.diffplug.gradle.spotless") version "3.10.0"
    id("com.jfrog.bintray") version "1.8.2"
    jacoco
    `maven-publish`
}

object Versions {
    /**
     * The version of KtLint to be used for linting the Kotlin and Kotlin Script files.
     */
    const val KTLINT = "0.23.1"
}

allprojects {
    apply {
        plugin("com.diffplug.gradle.spotless")
    }
    group = "de.nielsfalk.ktor"
    version = "0.3.1"

    repositories {
        mavenCentral()
        jcenter()
        maven { setUrl("https://dl.bintray.com/kotlin/ktor") }
    }
}

fun DependencyHandler.ktor(name: String) =
    create(group = "io.ktor", name = name, version = "0.9.3")

subprojects {
    apply {
        plugin("kotlin")
        plugin("java-library")
        plugin("jacoco")
    }

    dependencies {
        "api"(kotlin(module = "stdlib", version = property("kotlin.version") as String))
        "api"(kotlin(module = "reflect", version = property("kotlin.version") as String))
        "api"(ktor("ktor-locations"))
        "api"(ktor("ktor-server-core"))

        "testImplementation"(ktor("ktor-server-test-host"))
        "testImplementation"(ktor("ktor-gson"))
        "testImplementation"(group = "com.winterbe", name = "expekt", version = "0.5.0")
    }

    kotlin {
        // Enable coroutines supports for Kotlin.
        experimental.coroutines = Coroutines.ENABLE
    }

    spotless {
        kotlin {
            ktlint(Versions.KTLINT)
            trimTrailingWhitespace()
            endWithNewline()
        }
    }

    tasks.withType<Test> {
        extensions.configure(typeOf<JacocoTaskExtension>()) {
            /*
             * Fix for Jacoco breaking Build Cache support.
             * https://github.com/gradle/gradle/issues/5269
             */
            isAppend = false
        }
    }

    tasks.withType<JacocoReport> {
        reports {
            html.isEnabled = true
            xml.isEnabled = true
            csv.isEnabled = false
        }
    }
}

val jacocoRootReport = task<JacocoReport>("jacocoRootReport") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Generates code coverage report for all sub-projects."

    val jacocoReportTasks =
        subprojects.map { it.tasks["jacocoTestReport"] as JacocoReport }
    dependsOn(jacocoReportTasks)

    val allExecutionData = jacocoReportTasks.map { it.executionData }
    executionData(*allExecutionData.toTypedArray())

    // Pre-initialize these to empty collections to prevent NPE on += call below.
    additionalSourceDirs = files()
    sourceDirectories = files()
    classDirectories = files()

    subprojects.forEach { testedProject ->
        val sourceSets = testedProject.java.sourceSets
        this@task.additionalSourceDirs = this@task.additionalSourceDirs?.plus(files(sourceSets["main"].allSource.srcDirs))
        this@task.sourceDirectories += files(sourceSets["main"].allSource.srcDirs)
        this@task.classDirectories += files(sourceSets["main"].output)
    }

    reports {
        html.isEnabled = true
        xml.isEnabled = true
        csv.isEnabled = false
    }
}

allprojects {
    // Configures the Jacoco tool version to be the same for all projects that have it applied.
    pluginManager.withPlugin("jacoco") {
        // If this project has the plugin applied, configure the tool version.
        jacoco {
            toolVersion = "0.8.1"
        }
    }
}

project(":ktor-swagger") {
    apply(plugin = "com.jfrog.bintray")
    apply(plugin = "maven-publish")

    val sourceJarTask = task<Jar>("sourceJar") {
        from(java.sourceSets["main"].allSource)
        classifier = "sources"
    }

    val publicationName = "publication-$name"

    // This ensures that the entire project's configuration has been resolved before creating a publish artifact.
    publishing {
        publications {
            create<MavenPublication>(publicationName) {
                from(components["java"])
                artifact(sourceJarTask)
            }
        }
    }

    bintray {
        user = properties["bintray.publish.user"].toString()
        key = properties["bintray.publish.key"].toString()
        setPublications(publicationName)
        with(pkg) {
            userOrg = "ktor-swagger"
            repo = "maven-artifacts"
            name = "ktor-swagger"
            publish = true
            setLicenses("Apache-2.0")
            setLabels("ktor", "kotlin", "web server", "swagger")
            vcsUrl = "https://github.com/nielsfalk/ktor-swagger.git"
            githubRepo = "https://github.com/nielsfalk/ktor-swagger"
        }
    }
}


/**
 * Heroku will invoke this task if it is present.
 * https://devcenter.heroku.com/articles/deploying-gradle-apps-on-heroku#verify-that-your-build-file-is-set-up-correctly
 */
task("stage") {
    description = "Task executed by heroku to build this gradle project for deployment."
    group = "heroku"
    dependsOn(":ktor-sample-swagger:installDist")
}

task<Wrapper>("wrapper") {
    gradleVersion = "4.8"
    distributionType = Wrapper.DistributionType.ALL
}

/**
 * Configures the [kotlin][org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension] project extension.
 */
fun Project.`kotlin`(configure: org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension.() -> Unit): Unit =
    extensions.configure("kotlin", configure)

/**
 * Retrieves the [java][org.gradle.api.plugins.JavaPluginConvention] project convention.
 */
val Project.`java`: org.gradle.api.plugins.JavaPluginConvention
    get() = convention.getPluginByName("java")