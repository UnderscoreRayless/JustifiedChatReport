import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import cc.polyfrost.gradle.util.noServerRunConfigs

plugins {
    id("cc.polyfrost.multi-version")
    id("cc.polyfrost.defaults")
    id("com.github.johnrengelman.shadow")
    id("net.kyori.blossom") version "1.3.0"
    id("signing")
    java
}

val mod_name: String by project
val mod_version: String by project
val mod_id: String by project

preprocess {
    vars.put("MODERN", if (project.platform.mcMinor >= 16) 1 else 0)
}

blossom {
    replaceToken("@VER@", mod_version)
    replaceToken("@NAME@", mod_name)
    replaceToken("@ID@", mod_id)
}

version = mod_version
group = "net.wyvest"
base {
    archivesName.set("$mod_name ($platform)")
}
loom {
    noServerRunConfigs()
    if (project.platform.isLegacyForge) {
        launchConfigs.named("client") {
            arg("--tweakClass", "cc.polyfrost.oneconfigwrapper.OneConfigWrapper")
            property("mixin.debug.export", "true")
        }
    }
}

val shade: Configuration by configurations.creating {
    configurations.implementation.get().extendsFrom(this)
}

sourceSets {
    main {
        output.setResourcesDir(java.classesDirectory)
    }
}

repositories {
    maven("https://repo.polyfrost.cc/releases")
}

dependencies {
    modCompileOnly("cc.polyfrost:oneconfig-$platform:0.1.0-alpha+")

    if (platform.isLegacyForge) {
        shade("cc.polyfrost:oneconfig-wrapper-launchwrapper:1.0.0-alpha+")
    }
    modRuntimeOnly("me.djtheredstoner:DevAuth-" +
            (if (platform.isForge) { if (platform.isLegacyForge) "forge-legacy" else "forge-latest" } else "fabric")
            + ":1.1.0")
}

tasks.processResources {
    inputs.property("id", mod_id)
    inputs.property("name", mod_name)
    val java = if (project.platform.mcMinor >= 18) {
        17
    } else {
        if (project.platform.mcMinor == 17) 16 else 8
    }
    val compatLevel = "JAVA_${java}"
    inputs.property("java", java)
    inputs.property("java_level", compatLevel)
    inputs.property("version", mod_version)
    inputs.property("mcVersionStr", project.platform.mcVersionStr)
    filesMatching(listOf("mcmod.info", "mixins.${mod_id}.json", "mods.toml")) {
        expand(
                mapOf(
                        "id" to mod_id,
                        "name" to mod_name,
                        "java" to java,
                        "java_level" to compatLevel,
                        "version" to mod_version,
                        "mcVersionStr" to project.platform.mcVersionStr
                )
        )
    }
    filesMatching("fabric.mod.json") {
        expand(
                mapOf(
                        "id" to mod_id,
                        "name" to mod_name,
                        "java" to java,
                        "java_level" to compatLevel,
                        "version" to mod_version,
                        "mcVersionStr" to project.platform.mcVersionStr.substringBeforeLast(".") + ".x"
                )
        )
    }
}

tasks {
    withType(Jar::class.java) {
        if (project.platform.isFabric) {
            exclude("mcmod.info", "mods.toml")
        } else {
            exclude("fabric.mod.json")
            if (project.platform.isLegacyForge) {
                exclude("mods.toml")
            } else {
                exclude("mcmod.info")
            }
        }
    }
    named<ShadowJar>("shadowJar") {
        archiveClassifier.set("dev")
        configurations = listOf(shade)
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }
    remapJar {
        input.set(shadowJar.get().archiveFile)
        archiveClassifier.set("")
    }
    jar {
        manifest {
            attributes(
                    mapOf(
                            "ModSide" to "CLIENT",
                            "ForceLoadAsMod" to true,
                            "TweakOrder" to "0",
                            "TweakClass" to "cc.polyfrost.oneconfigwrapper.OneConfigWrapper"
                    )
            )
        }
        dependsOn(shadowJar)
        archiveClassifier.set("")
        enabled = false
    }
}