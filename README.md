# BlackNote

Minimal black notes app for Android (built for the OnePlus 15R / OxygenOS).
Every note you write is also posted as an **ongoing notification** that cannot be
swiped away or dismissed from the notification shade. A note's notification can
only be removed by deleting the note inside the app.

## How the pinning works

- Each note posts a notification with `setOngoing(true)`. Ongoing notifications
  cannot be dismissed by swipe on any Android version.
- A `NotificationListenerService` watches for any forced removal of the app's
  notifications and immediately re-posts them (opt-in: enable notification access
  for BlackNote in system settings, or use the in-app link).
- A `BOOT_COMPLETED` receiver re-posts all notes after a reboot.
- Deleting a note in the app is the only way to clear its notification.

## Physics / engineering solver

Tap the mic icon on the Notes screen to open the **solver**: a blank black page
with a single microphone button. State your physics or engineering problem out
loud (or type it in the field at the bottom). The transcript is sent to an LLM
(Mistral, OpenAI, or any OpenAI-compatible custom endpoint) which picks the best
formula(s), explains every variable with its unit, and gives an estimate of the
answer. Pure black background, white text, fluid Compose animations.

API connection (gear icon on the solver screen, stored locally on the device):

1. Pick a provider: **Mistral** (`api.mistral.ai`), **OpenAI**
   (`api.openai.com`), or **Custom** (any OpenAI-compatible base URL, e.g. a
   local Ollama/LM Studio server at `http://192.168.x.x:1234/v1`).
2. Paste your API key (for local servers without auth, any non-empty value works).
3. Optionally set the model (defaults: `mistral-large-latest`, `gpt-4o-mini`).

Voice input uses the on-device `SpeechRecognizer`, so no extra API key is needed
for transcription; the app only needs `INTERNET` for the LLM call and
`RECORD_AUDIO` for the microphone.

## Build

Requires Android Studio (Ladybug or newer) with SDK platform 35.

```bash
./gradlew assembleDebug
```

Install on the phone:

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

Or open the project folder in Android Studio and press Run.

## First run

1. Grant the notification permission when asked.
2. Optionally tap "Keep notifications pinned" and enable notification access for
   BlackNote so notifications are instantly restored if the system clears them.

## Project layout

```
app/src/main/java/com/example/blacknote/
├── MainActivity.kt          black Compose UI: add / list / delete notes
├── SolverActivity.kt        voice-in physics/engineering solver screen
├── LlmClient.kt             chat-completions call (Mistral / OpenAI / custom)
├── SolverStore.kt           SharedPreferences for provider / key / model / URL
├── MarkdownText.kt          minimal Markdown-lite renderer for LLM answers
├── Note.kt                  note model
├── NoteStore.kt             SharedPreferences persistence
├── NoteNotifier.kt          posts ongoing (non-dismissible) notifications
├── NoteListenerService.kt   re-posts notifications if the system removes them
└── BootReceiver.kt          restores notifications after reboot
```
