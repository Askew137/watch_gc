// Shared release-signing *settings* for :mobile and :wear (applied from each module, which
// then wires them into its own `android { signingConfigs / buildTypes }`).
//
// BOTH apps must be signed with the SAME key: the Wearable Data Layer matches phone and
// watch apps by package name *and* signing certificate, and the in-app updater needs
// every release to be signed with one stable key.
//
// Configure with environment variables (CI) or ~/.gradle/gradle.properties (local):
//   GCWATCH_KEYSTORE_FILE     / gcwatch.keystore.file
//   GCWATCH_KEYSTORE_PASSWORD / gcwatch.keystore.password
//   GCWATCH_KEY_ALIAS         / gcwatch.key.alias
//   GCWATCH_KEY_PASSWORD      / gcwatch.key.password
// Without them the release build falls back to the debug key, so local builds stay
// installable; such APKs must NOT be published.

fun setting(env: String, prop: String): String? =
    providers.environmentVariable(env).orNull?.takeIf { it.isNotBlank() }
        ?: providers.gradleProperty(prop).orNull?.takeIf { it.isNotBlank() }

val keystoreFile = setting("GCWATCH_KEYSTORE_FILE", "gcwatch.keystore.file")
val keystorePassword = setting("GCWATCH_KEYSTORE_PASSWORD", "gcwatch.keystore.password")
val keyAlias = setting("GCWATCH_KEY_ALIAS", "gcwatch.key.alias")
val keyPassword = setting("GCWATCH_KEY_PASSWORD", "gcwatch.key.password")

val complete = listOf(keystoreFile, keystorePassword, keyAlias, keyPassword).all { it != null }
if (!complete) {
    logger.warn("[${project.name}] No release keystore configured – signing release with the DEBUG key (do not publish).")
}

extra["releaseKeystore"] = if (complete) {
    mapOf(
        "file" to keystoreFile!!,
        "storePassword" to keystorePassword!!,
        "keyAlias" to keyAlias!!,
        "keyPassword" to keyPassword!!,
    )
} else {
    null
}
