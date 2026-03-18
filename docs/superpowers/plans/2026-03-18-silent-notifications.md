# Silent Notification Updates Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement silent notification updates to prevent sound/vibration alerts every time the blocked list changes.

**Architecture:** 
- Lower the notification channel importance.
- Configure the builder to only alert once.

**Tech Stack:** Kotlin, Android Notification API

---

### Task 1: Implement Silent Notifications

**Files:**
- Modify: `app/src/main/java/com/netzone/app/NetZoneVpnService.kt`

- [ ] **Step 1: Update createNotificationChannel.** Change the importance to `IMPORTANCE_LOW`.

```kotlin
// Change this:
val channel = NotificationChannel(
    CHANNEL_ID, "Firewall Status", NotificationManager.IMPORTANCE_DEFAULT
)
// To this:
val channel = NotificationChannel(
    CHANNEL_ID, "Firewall Status", NotificationManager.IMPORTANCE_LOW
)
```

- [ ] **Step 2: Update createNotification.** Add `setOnlyAlertOnce(true)` to the builder.

```kotlin
// Update the builder chain:
return NotificationCompat.Builder(this, CHANNEL_ID)
    .setSmallIcon(R.mipmap.ic_launcher)
    .setContentTitle("NetZone Firewall")
    .setContentText(content)
    .setContentIntent(pendingIntent)
    .setPriority(NotificationCompat.PRIORITY_LOW)
    .setOngoing(true)
    .setOnlyAlertOnce(true) // Add this line
    .build()
```

- [ ] **Step 3: Commit changes.**

```bash
git add app/src/main/java/com/netzone/app/NetZoneVpnService.kt
git commit -m "feat: implement silent notification updates"
```

### Task 2: Verification

- [ ] **Step 1: Build the project.**
Run: `./gradlew assembleDebug`

- [ ] **Step 2: Logical check.** Verify that `updateNotification` will now trigger silent updates because both the channel importance and the `setOnlyAlertOnce` flag are set correctly.
