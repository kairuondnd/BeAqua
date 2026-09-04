pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.10.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven {
            url = uri("https://api.mapbox.com/downloads/v2/releases/maven")
            authentication {
                create<BasicAuthentication>("basic")
            }
            credentials {
                // Do not change the username ==> it should always be "mapbox"
                username = "mapbox"
                // Use the secret token you provided
                password = "sk.eyJ1Ijoia3lsZS1rdW1hbGFvIiwiYSI6ImNtbnlzcDV2ZDA1eHgyb3I4Mjd6b2pvMjEifQ.Yi6z_jG4H4f325-U_UE5vA"
            }
        }
    }
}

rootProject.name = "BeAqua"
include(":app")
