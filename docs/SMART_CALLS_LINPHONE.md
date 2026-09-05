# Smart Calls 2.0: direct Android SIP

Version 2.0 (versionCode 11) replaces the active Smart Calls backend with
org.linphone:linphone-sdk-android:5.5.18 from the official Linphone Maven repository.
The visible screen heading is “Smart Calls · 2.0” with a Linphone subtitle.

## Why

The custom SIP client repeatedly lost real Easybell calls after about 20–30 seconds.
Earlier changes to ACKs, stream framing and keepalives did not establish reliable
long calls. The root cause has not been proven by a real call trace.
This change delegates SIP transactions, dialog routing, session refresh, SDP,
RTP pacing and SRTP to liblinphone instead of adding more custom protocol patches.

## Runtime

- SmartCallsTab uses the application-scoped LinphoneSipClient singleton. The old
  NativeSipClient remains in source for historical reference and its shared model
  types, but is not instantiated by Smart Calls and is not a fallback.
- The existing encrypted account preferences are reused; the SDK configuration is
  memory-only with auth-info persistence disabled. No Linphone account is needed.
- Registration uses the chosen registrar, transport, port, SIP user and optional
  authentication user. The selected registrar also serves as outbound proxy.
- TLS requires SRTP. Certificate and hostname verification remain enabled.
  PCMA and PCMU are enabled; video is disabled.
- SmartCallService extends the SDK CoreService. It enters foreground with the
  microphone type before placing the call, has an Auflegen notification action,
  and holds a partial wake lock only while foreground. No phone-call role or
  rented server is introduced.
- SDK access and its automatic iteration run on the Android main thread.
  Leaving the tab no longer disconnects the account or call. Opening it again
  reuses the session and retains the mute/speaker settings.
- Duration is read from the SDK call. There is no application maximum call
  duration, and transport failures are not hidden behind an “Im Gespräch” timer.
- The native call recorder writes WAV into smart_calls_recordings. A recording
  is published to the UI only after the SDK releases the call and finalizes it.
  The existing optional SAF export, Gemini processing and summary-only storage
  path remain in place. The UI processes completed recordings on return to the
  tab as well. No audio upload to Supabase is introduced.

## Verification

The adapter and service were compiled for JVM 11 against the actual 5.5.18 AAR
classes, Android API classes and Kotlin/coroutines. The unchanged app storage
class and shared models were represented by compile-only stubs during this check.
This is an API/type check, not a full APK build or an Android runtime test.

The repository has gradlew but does not contain its gradle/wrapper files, and the
execution environment has no Android SDK installation. Build the complete APK
through the existing Android/AI Studio build process before installing.

Required device acceptance:

1. Open Agents → Smart Calls and confirm the 2.0/Linphone heading and automatic
   Easybell registration using the existing credentials.
2. Grant microphone access and call a test destination. Verify audio in both
   directions for at least two minutes, then for a full hour.
3. Repeat with screen off and while visiting another tab. Verify both endpoints
   stay connected, not just the local duration display.
4. Hang up remotely, locally and from the notification; check immediate UI end,
   stopped recording, removal of notification and release of the wake lock.
5. Listen to the WAV for both voices and check that calls over 60 seconds still
   enter the existing summary workflow. Verify optional configured SAF export.
6. Deny microphone access and retry, call a busy destination, then make a second
   successful call. Check that error handling does not strand the UI in a call.

A successful hour-long Easybell call has not yet been observed for this change.
No claim of a verified final fix should be made until the device test passes.

## Dependency notice

liblinphone is provided by Belledonne Communications under GPLv3 (or a separately
obtained commercial license). This integration targets the owner's stated
personal use. It does not change or assert the license of unrelated repository
code. Preserve dependency notices and review distribution obligations if APKs
are later distributed.

- SDK documentation: https://download.linphone.org/releases/docs/liblinphone/5.5/java/
- SDK sources: https://gitlab.linphone.org/BC/public/linphone-sdk
- License: https://www.gnu.org/licenses/gpl-3.0.html
