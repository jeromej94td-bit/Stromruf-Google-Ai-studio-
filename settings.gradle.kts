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

plugins { id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0" }

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    maven {
      url = uri("https://download.linphone.org/maven_repository/")
      content { includeGroup("org.linphone") }
    }

    // Official sherpa-onnx Android AAR. Keep this pinned to a release asset instead
    // of a third-party Maven mirror so the native ASR runtime comes from k2-fsa.
    ivy {
      name = "SherpaOnnxGithubReleases"
      url = uri("https://github.com/k2-fsa/sherpa-onnx/releases/download")
      patternLayout {
        artifact("v[revision]/[artifact]-[revision].[ext]")
      }
      metadataSources { artifact() }
      content { includeModule("com.k2fsa", "sherpa-onnx") }
    }

    google()
    mavenCentral()
  }
}

rootProject.name = "Stromruf"

include(":app")
