# NetZone Smoke Test Checklist

## Preflight
- Install the debug APK.
- Launch the app on a device or emulator.
- Confirm notifications, VPN, usage access, and exact alarm permissions.

## Core Flow
- Open the app and confirm the Home screen loads.
- Start the VPN and accept the system permission dialog.
- Stop and restart the VPN.
- Toggle Dark Mode and confirm the theme updates.

## Navigation
- Open Apps, Logs, and Settings from the bottom bar.
- Open About and Support from Settings.
- Return back from each screen without crashes.

## Apps Screen
- Search for an app by name and by package name.
- Toggle WiFi and Mobile blocking on one app.
- Expand an app card and change schedule and daily limit values.
- Confirm blocked state indicators update correctly.

## Logs
- Trigger a blocked request.
- Confirm the log entry appears.
- Clear logs and confirm the list becomes empty.

## Settings
- Toggle system-app visibility.
- Update custom DNS.
- Toggle screen-off blocking.

## Expected Results
- No crashes.
- VPN starts and stops cleanly.
- Navigation stays consistent.
- UI updates immediately after changes.
