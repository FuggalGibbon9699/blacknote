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

## App drawer

Tap the **☰ menu** (top left) or **swipe from the left edge** to open the drawer:
**Notes** and **Solver** are the apps, and **API settings** sits at the bottom
under a key icon. The top bar respects status bar / camera cutout / navigation
bar insets on any phone.

## Physics / engineering solver

The **solver** is a blank black page with a single solid white microphone button.
State your physics or engineering problem out loud (or type it in the rounded
field at the bottom). The transcript is sent to an LLM (Mistral, OpenAI, or any
OpenAI-compatible custom endpoint) which returns a structured answer: a short
explanation on top, the best formula as a big interactive LaTeX equation in the
middle, and the variables with units listed below.

The equation is interactive (all rearranging and calculating happens in-app,
not via the LLM):

- **Hold down a variable** in the formula → the equation is rearranged to express
  that variable (e.g. `F = m·a` → hold `a` → `a = F/m`) with a fluid animation.
- **Tap a variable** → a numeric keyboard pops up; entered values are substituted
  into the equation. Once every variable has a value, the app evaluates the
  result with its built-in expression calculator and shows e.g. `a = 3 m/s²`.

API connection (**☰ → API settings**, stored locally on the device):

1. Pick a provider: **Mistral** (`api.mistral.ai`), **OpenAI**
   (`api.openai.com`), or **Custom** (any OpenAI-compatible base URL, e.g. a
   local Ollama/LM Studio server at `http://192.168.x.x:1234/v1`).
2. Paste your API key (for local servers without auth, any non-empty value works).
3. Pick a model: for Mistral there are quick presets (Smallest / Small / Medium /
   Large / Latest) plus a free-text field for any other capable model
   (defaults: `mistral-large-latest`, `gpt-4o-mini`).

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
├── MainActivity.kt          app root: navigation drawer + top bar (safe-area aware)
├── SolverScreen.kt          voice-in physics/engineering solver screen
├── SettingsScreen.kt        API settings (provider / key / model presets / URL)
├── LlmClient.kt             chat-completions call, structured JSON answer parsing
├── Expr.kt                  in-app expression parser / evaluator / equation rearranger
├── LatexView.kt             mini LaTeX renderer with interactive variable annotations
├── Ui.kt                    shared black-theme text field styles
├── MarkdownText.kt          minimal Markdown-lite renderer for raw LLM answers
├── SolverStore.kt           SharedPreferences for provider / key / model / URL
├── Note.kt                  note model
├── NoteStore.kt             SharedPreferences persistence
├── NoteNotifier.kt          posts ongoing (non-dismissible) notifications
├── NoteListenerService.kt   re-posts notifications if the system removes them
└── BootReceiver.kt          restores notifications after reboot
```
