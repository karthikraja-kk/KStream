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
include(":app")
include(":core:common")
include(":core:model")
include(":core:network")
include(":core:data")
include(":core:domain")
include(":core:ui")
include(":feature:welcome")
include(":feature:home")
include(":feature:details")
include(":feature:player")
include(":feature:downloads")
include(":feature:search")
include(":feature:settings")
