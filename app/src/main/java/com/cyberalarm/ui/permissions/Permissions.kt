package com.cyberalarm.ui.permissions

import android.Manifest
import android.app.AlarmManager
import android.app.AppOpsManager
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.cyberalarm.enforcement.CyberAccessibilityService

enum class PermissionTier { CORE, ENFORCER, OPTIONAL }

/**
 * One row in the permissions dashboard. Either [runtime] (a runtime permission to request in-app)
 * or [settingsIntent] (a special access the user must flip in system settings) drives the grant.
 */
data class PermissionItem(
    val key: String,
    val title: String,
    val rationale: String,
    val tier: PermissionTier,
    val granted: Boolean,
    val runtime: String? = null,
    val settingsIntent: Intent? = null,
)

object Permissions {

    fun all(context: Context): List<PermissionItem> = buildList {
        val pkgUri = Uri.parse("package:${context.packageName}")

        add(
            PermissionItem(
                "exact", "EXACT_ALARMS",
                "Fires the alarm at the exact minute, even in Doze. Without it Android may delay alarms.",
                PermissionTier.CORE,
                Build.VERSION.SDK_INT < 31 || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms(),
                settingsIntent = if (Build.VERSION.SDK_INT >= 31) Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkgUri) else null,
            ),
        )
        if (Build.VERSION.SDK_INT >= 33) {
            add(
                PermissionItem(
                    "notif", "NOTIFICATIONS",
                    "The ringing alarm is a notification. Blocked notifications = no alarm on the lock screen.",
                    PermissionTier.CORE,
                    granted(context, Manifest.permission.POST_NOTIFICATIONS),
                    runtime = Manifest.permission.POST_NOTIFICATIONS,
                ),
            )
        }
        if (Build.VERSION.SDK_INT >= 34) {
            add(
                PermissionItem(
                    "fsi", "FULL_SCREEN_ALARM",
                    "Lets the alarm take over the screen when the phone is locked, instead of a small banner.",
                    PermissionTier.CORE,
                    context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent(),
                    settingsIntent = Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, pkgUri),
                ),
            )
        }
        add(
            PermissionItem(
                "overlay", "DISPLAY_OVER_APPS",
                "If you leave the alarm, a shield covers the screen and the alarm is brought back. Also lets the app re-open the alarm from the background.",
                PermissionTier.ENFORCER,
                Settings.canDrawOverlays(context),
                settingsIntent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, pkgUri),
            ),
        )
        add(
            PermissionItem(
                "a11y", "ACCESSIBILITY_ENFORCER",
                "Closes the power menu while ringing and shows the block screen on locked apps. Only window names are read, never content. " +
                    "If the toggle is greyed out (Android 13+ sideload): App info > ⋮ > Allow restricted settings.",
                PermissionTier.ENFORCER,
                isAccessibilityEnabled(context),
                settingsIntent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS),
            ),
        )
        add(
            PermissionItem(
                "usage", "USAGE_ACCESS",
                "Backup detector for the app lock: tells which app is in front when the accessibility enforcer is off.",
                PermissionTier.ENFORCER,
                hasUsageAccess(context),
                settingsIntent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS),
            ),
        )
        add(
            PermissionItem(
                "battery", "UNRESTRICTED_BATTERY",
                "Stops aggressive battery savers from killing the alarm service mid-ring.",
                PermissionTier.ENFORCER,
                context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName),
                settingsIntent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkgUri),
            ),
        )
        val audio = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
        add(
            PermissionItem(
                "audio", "MUSIC_LIBRARY",
                "Lists your music so any track can be an alarm sound. (Picking a single file via BROWSE works without it.)",
                PermissionTier.OPTIONAL,
                granted(context, audio),
                runtime = audio,
            ),
        )
        if (Build.VERSION.SDK_INT >= 29) {
            add(
                PermissionItem(
                    "activity", "STEP_SENSOR",
                    "Uses the hardware step detector for the STEP_DRIVE challenge. Without it, motion is counted from the accelerometer.",
                    PermissionTier.OPTIONAL,
                    granted(context, Manifest.permission.ACTIVITY_RECOGNITION),
                    runtime = Manifest.permission.ACTIVITY_RECOGNITION,
                ),
            )
        }
    }

    /** Install-time permissions: always granted, listed so the dashboard is complete. */
    val installTime = listOf("RECEIVE_BOOT_COMPLETED", "WAKE_LOCK", "VIBRATE", "FOREGROUND_SERVICE")

    fun coreMissing(context: Context): Boolean = all(context).any { it.tier == PermissionTier.CORE && !it.granted }

    fun enforcerMissing(context: Context): Boolean = all(context).any { it.tier == PermissionTier.ENFORCER && !it.granted }

    fun granted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun isAccessibilityEnabled(context: Context): Boolean {
        val expected = ComponentName(context, CyberAccessibilityService::class.java)
        val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == expected }
    }

    fun hasUsageAccess(context: Context): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java)
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun appDetails(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
}
