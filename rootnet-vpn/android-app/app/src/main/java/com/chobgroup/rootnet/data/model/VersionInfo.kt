package com.chobgroup.rootnet.data.model

/** Version-gate info — spec §6.4. */
data class VersionInfo(
    val hasUpdate: Boolean,
    val forceUpdate: Boolean,
    val isBelowMinimum: Boolean,
    val latestVersion: String,
    val latestBuild: Int,
    val minimumVersion: String,
    val updateUrl: String,
    val releaseNotes: String,
)

/**
 * Semver compare on 3 numeric segments (spec §6.4):
 * returns 1 if a > b, -1 if a < b, 0 if equal.
 */
fun compareVersions(a: String, b: String): Int {
    val aParts = a.split(".").mapNotNull { it.toIntOrNull() }
    val bParts = b.split(".").mapNotNull { it.toIntOrNull() }
    for (i in 0 until 3) {
        val av = aParts.getOrElse(i) { 0 }
        val bv = bParts.getOrElse(i) { 0 }
        if (av > bv) return 1
        if (av < bv) return -1
    }
    return 0
}

/** Build a [VersionInfo] from the API payload + local app version. */
fun buildVersionInfo(
    currentVersion: String,
    currentBuild: Int,
    latestVersion: String,
    latestBuild: Int,
    minimumVersion: String,
    forceUpdate: Boolean,
    updateUrl: String,
    releaseNotes: String,
): VersionInfo {
    val isBelowMinimum = minimumVersion.isNotBlank() && compareVersions(minimumVersion, currentVersion) > 0
    val hasUpdate = (latestVersion.isNotBlank() && compareVersions(latestVersion, currentVersion) > 0) ||
        latestBuild > currentBuild
    return VersionInfo(
        hasUpdate = hasUpdate,
        forceUpdate = forceUpdate || isBelowMinimum,
        isBelowMinimum = isBelowMinimum,
        latestVersion = latestVersion,
        latestBuild = latestBuild,
        minimumVersion = minimumVersion,
        updateUrl = updateUrl,
        releaseNotes = releaseNotes,
    )
}
