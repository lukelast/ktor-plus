package net.ghue.ktp.gradle.project

import org.gradle.api.JavaVersion

/**
 * The JDK apps compile for and run on; the virtual-thread-per-request model needs JEP 491 (24+).
 */
internal const val APP_JDK = 25

/**
 * Bytecode level of published libraries and of multiplatform JVM targets, so any consumer on a
 * current toolchain can use them (a Compose desktop app on JetBrains Runtime 21, say).
 */
internal const val LIBRARY_JDK = 21

/**
 * The toolchain to compile with: the app floor, or the JVM running the build when that is newer.
 * Toolchain specs cannot express "25 or newer", and provisioning a second JDK next to a newer local
 * one is waste; release semantics keep the bytecode at the floor either way.
 */
internal fun buildToolchainJdk(): Int = maxOf(APP_JDK, JavaVersion.current().majorVersion.toInt())
