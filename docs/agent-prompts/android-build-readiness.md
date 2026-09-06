# Android Build Readiness Prompt

Use this prompt when asking another AI agent to repair Stromruf before installing from GitHub main.

```text
You are working on the Stromruf Android repository. Before changing features, make GitHub main buildable as a debug APK.

Run the Android debug build locally:

./gradlew :app:assembleDebug --no-daemon --console=plain

If the repo has no wrapper, use the locally configured Gradle/JDK/Android SDK on the machine, but keep the command equivalent to :app:assembleDebug.

Check and fix these recurring build breakers:

1. In app/src/main/java/com/example/homesip/HomeSipTrunk.kt, ContextCompat must come from androidx.core.content.ContextCompat, not androidx.core.app.ContextCompat. startForegroundService is on the content ContextCompat.
2. In app/src/main/java/com/example/ui/screens/HomeSipTrunkCard.kt, statusColor reads Compose theme colors, so mark it @Composable before private fun statusColor(...).
3. In app/src/main/java/com/example/ui/screens/ActivityCalendar.kt, do not smart-cast the delegated Compose state lastSyncAt directly. Copy it first, for example val currentLastSyncAt = lastSyncAt, then use currentLastSyncAt in the null check and Date(...).

After fixing, run :app:assembleDebug again and only report success if Gradle exits with BUILD SUCCESSFUL. Do not rename the package or app label in GitHub main; the Stromi 2 package rename is only for temporary side-by-side phone installs.
```

