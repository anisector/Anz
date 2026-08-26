pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal(); maven("https://jitpack.io") }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories { google(); mavenCentral(); maven("https://jitpack.io") }
}
rootProject.name = "anizium-modern"
include(":Anizium")
