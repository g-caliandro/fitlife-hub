plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.roborazzi) apply false
    alias(libs.plugins.spotless)
}

spotless {
    kotlin {
        target("app/src/**/*.kt")
        ktlint(libs.versions.ktlint.get()).editorConfigOverride(mapOf("max_line_length" to "off", "ktlint_standard_max-line-length" to "disabled", "ktlint_standard_no-wildcard-imports" to "disabled", "ktlint_standard_backing-property-naming" to "disabled"))
    }
    kotlinGradle {
        target("*.kts", "app/*.kts")
        ktlint(libs.versions.ktlint.get()).editorConfigOverride(mapOf("max_line_length" to "off", "ktlint_standard_max-line-length" to "disabled", "ktlint_standard_no-wildcard-imports" to "disabled", "ktlint_standard_backing-property-naming" to "disabled"))
    }
}
