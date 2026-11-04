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
├── Note.kt                  note model
├── NoteStore.kt             SharedPreferences persistence
├── NoteNotifier.kt          posts ongoing (non-dismissible) notifications
├── NoteListenerService.kt   re-posts notifications if the system removes them
└── BootReceiver.kt          restores notifications after reboot
```
