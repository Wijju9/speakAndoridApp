# Speak Android App (Kotlin)

Voice recording app with realtime English speech-to-text, local audio storage, replay support, and Hindi/Gujarati translation.

## Features

- Start/Stop recording with live English transcription shown while speaking.
- Handles long sessions (2+ hours) with live duration timer.
- Saves each session in local Room database with:
  - audio file path
  - English text
  - Hindi translation
  - Gujarati translation
- Replay saved recordings from the history list.
- CI/CD GitHub Action builds an installable debug APK and uploads artifact.

## Run locally

1. Open in Android Studio (JDK 17, Android SDK 34).
2. Let Gradle sync.
3. Run app on physical Android device.
4. Grant microphone permission.

## Install APK from CI

1. Push to GitHub.
2. Open **Actions** tab, run **Android CI/CD APK** workflow.
3. Download artifact `speak-android-debug-apk`.
4. Transfer APK to phone and install (allow unknown apps).

## Notes

- Speech recognition uses Android `SpeechRecognizer` (requires Google speech services).
- Translation uses ML Kit Translate and downloads language models as needed.
- For very long recordings, keep app in foreground and disable battery restrictions for best stability.
