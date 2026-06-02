pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "KStream"
include(":app-tv")
include(":core:common")
include(":core:model")
include(":core:network")
include(":core:data")
include(":core:domain")
include(":core:enrichment")
include(":feature:home")
include(":feature:details")
include(":feature:player")
include(":feature:downloads")
include(":feature:search")
include(":feature:settings")
