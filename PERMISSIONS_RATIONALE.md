# NetZone Permissions Rationale

This document explains why NetZone requests each Android permission.

## Core permissions

- `android.permission.INTERNET`
  - Needed by `VpnService` based apps for network stack interactions.
- `android.permission.ACCESS_NETWORK_STATE`
  - Needed to apply different block rules on Wi-Fi vs mobile data.
- `android.permission.FOREGROUND_SERVICE`
  - Required because the VPN engine runs as a foreground service.
- `android.permission.FOREGROUND_SERVICE_SPECIAL_USE`
  - Required for Android 14+ foreground service type `specialUse` used by the VPN service.
- `android.permission.POST_NOTIFICATIONS`
  - Needed to display foreground-service notifications on Android 13+.
- `android.permission.RECEIVE_BOOT_COMPLETED`
  - Needed to restore schedule-driven VPN behavior after device reboot.

## Policy-sensitive permissions

- `android.permission.PACKAGE_USAGE_STATS`
  - Used to enforce per-app daily usage quotas.
  - Only aggregate foreground usage duration is read; no data leaves device.

- `android.permission.QUERY_ALL_PACKAGES`
  - Required because firewall controls are package-scoped and the app must enumerate installed apps to present block toggles.
  - The list is used locally to build the app rules screen.

- `android.permission.SCHEDULE_EXACT_ALARM`
  - Needed for exact, user-configured schedule boundaries where access changes at precise times.

NetZone does not rely on analytics, ads SDKs, or remote policy engines for firewall decisions.
