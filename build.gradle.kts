plugins {
    alias(libs.plugins.kotlin) apply false
    alias(libs.plugins.versionCheck)
}

subprojects {
    if (projectDir.parentFile.name == "libs") {
        group = "com.github.lukelast.ktor-plus"
        ext["ktp.mode"] = "library"
    }
    // The plugin injects ktp-ktor/ktp-test/ktp-bom by their external coordinates; build them from
    // current sources here instead of resolving a previously published snapshot.
    configurations.all {
        resolutionStrategy.dependencySubstitution {
            substitute(module("com.github.lukelast.ktor-plus:ktp-ktor"))
                .using(project(":libs:ktp-ktor"))
            substitute(module("com.github.lukelast.ktor-plus:ktp-test"))
                .using(project(":libs:ktp-test"))
            substitute(platform(module("com.github.lukelast.ktor-plus:ktp-bom")))
                .using(platform(project(":libs:ktp-bom")))
        }
    }
}

tasks.withType<com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask> {
    rejectVersionIf {
        fun String.isNonStable() = "^[0-9,.v-]+(-r)?$".toRegex().matches(this).not()
        candidate.version.isNonStable() && !currentVersion.isNonStable()
    }
}

tasks.register<Delete>("clean") { delete(rootProject.layout.buildDirectory) }

// Apps receive the platform only through published POMs and module files; check those, not the
// build scripts.
val checkPublishedPlatform =
    tasks.register("checkPublishedPlatform") {
        group = LifecycleBasePlugin.VERIFICATION_GROUP
        description = "Checks that ktp-bom constrains every lib and that every lib imports it."
        val libs = subprojects.filter { it.parent?.name == "libs" }
        val bom = project(":libs:ktp-bom")
        val others = libs - bom
        val expectedImports =
            bom.configurations
                .getByName("api")
                .dependencies
                .filterIsInstance<ModuleDependency>()
                .filter {
                    it.attributes.getAttribute(Category.CATEGORY_ATTRIBUTE)?.name ==
                        Category.REGULAR_PLATFORM
                }
                .map { it.name }
                .toSet()
        val expectedConstraints = others.map { it.name }.toSet()
        fun Project.publication(file: String) =
            layout.buildDirectory.file("publications/mavenJava/$file")
        val bomPom = bom.publication("pom-default.xml")
        val libPoms = others.associate { it.name to it.publication("pom-default.xml") }
        val libModules = others.associate { it.name to it.publication("module.json") }
        // JitPack builds publish POMs only, and a module.json left in build/ from before is stale.
        val checkModules = System.getenv("JITPACK") == null
        libs.forEach { dependsOn("${it.path}:generatePomFileForMavenJavaPublication") }
        if (checkModules) {
            others.forEach { dependsOn("${it.path}:generateMetadataFileForMavenJavaPublication") }
        }
        doLast {
            fun managed(pom: File): List<Pair<String, String?>> {
                val doc =
                    javax.xml.parsers.DocumentBuilderFactory.newInstance()
                        .newDocumentBuilder()
                        .parse(pom)
                val nodes = doc.getElementsByTagName("dependencyManagement")
                if (nodes.length == 0) return emptyList()
                val deps = (nodes.item(0) as org.w3c.dom.Element).getElementsByTagName("dependency")
                return (0 until deps.length).map { i ->
                    val dep = deps.item(i) as org.w3c.dom.Element
                    fun text(tag: String) = dep.getElementsByTagName(tag).item(0)?.textContent
                    text("artifactId")!! to text("scope")
                }
            }
            val bomEntries = managed(bomPom.get().asFile)
            val imports = bomEntries.filter { it.second == "import" }.map { it.first }.toSet()
            val constraints = bomEntries.filter { it.second != "import" }.map { it.first }.toSet()
            check(imports == expectedImports) { "ktp-bom POM imports $imports, expected $expectedImports" }
            check(constraints == expectedConstraints) {
                "ktp-bom POM constrains $constraints, expected $expectedConstraints"
            }
            libPoms.forEach { (name, pom) ->
                check(("ktp-bom" to "import") in managed(pom.get().asFile)) {
                    "$name's POM does not import ktp-bom"
                }
            }
            if (!checkModules) return@doLast
            libModules.forEach { (name, module) ->
                val file = module.get().asFile
                @Suppress("UNCHECKED_CAST")
                val variants =
                    (groovy.json.JsonSlurper().parse(file) as Map<String, Any>)["variants"]
                        as List<Map<String, Any>>
                val api = variants.single { it["name"] == "apiElements" }
                val deps = (api["dependencies"] as List<Map<String, Any>>?).orEmpty()
                check(deps.any { it["module"] == "ktp-bom" }) {
                    "$name's apiElements do not carry ktp-bom"
                }
            }
        }
    }

tasks.register("check") {
    description = "Runs all the tests/verification tasks on all projects."
    dependsOn(gradle.includedBuild("ktp-gradle-plugin").task(":check"))
    dependsOn(gradle.includedBuild("ktp-gradle-plugin").task(":validatePlugins"))
    dependsOn(checkPublishedPlatform)
}

tasks.register("publishToMavenLocal") {
    dependsOn(gradle.includedBuild("ktp-gradle-plugin").task(":publishToMavenLocal"))
    subprojects.forEach { subproject ->
        if (subproject.projectDir.parentFile.name == "libs") {
            dependsOn(subproject.tasks.named("publishToMavenLocal"))
        }
    }
}

tasks.register("publish") {
    description = "Publishes libraries and plugins to Nexus"
    dependsOn(gradle.includedBuild("ktp-gradle-plugin").task(":publish"))
}

// Download the Gradle source for debugging.
tasks.wrapper { distributionType = Wrapper.DistributionType.ALL }
