package com.hyperlog.app.update

/**
 * A release's version, from its tag `vMAJOR.MINOR.PATCH` (SPEC, Distribution). [code] is the
 * Android versionCode the build gets from the same tag, so a later release always has a higher one.
 */
data class AppVersion(val major: Int, val minor: Int, val patch: Int) {
    val name: String get() = "$major.$minor.$patch"
    val code: Int get() = major * 10_000 + minor * 100 + patch

    companion object {
        private val Tag = Regex("""v?(\d{1,5})\.(\d{1,2})\.(\d{1,2})""")

        /** `v0.2.0` or `0.2.0`; null for anything else, minor or patch over 99 included. */
        fun parse(tag: String): AppVersion? = Tag.matchEntire(tag)?.groupValues?.let { (_, major, minor, patch) ->
            AppVersion(major.toInt(), minor.toInt(), patch.toInt())
        }
    }
}
