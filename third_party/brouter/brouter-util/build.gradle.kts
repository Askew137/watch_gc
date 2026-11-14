plugins {
    `java-library`
}

// Vendored upstream sources: keep them untouched; just compile them.
java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}
