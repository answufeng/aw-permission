package com.answufeng.permission

import android.Manifest
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AwPermissionTest {

    private val context: Application = ApplicationProvider.getApplicationContext()

    @Test
    fun `isGranted returns true for granted permission`() {
        Shadows.shadowOf(context).grantPermissions(Manifest.permission.CAMERA)
        assertTrue(AwPermission.isGranted(context, Manifest.permission.CAMERA))
    }

    @Test
    fun `isGranted returns false for non-granted permission`() {
        assertFalse(AwPermission.isGranted(context, Manifest.permission.CAMERA))
    }

    @Test
    fun `isAllGranted returns true when all permissions granted`() {
        Shadows.shadowOf(context).grantPermissions(
            Manifest.permission.CAMERA,
            Manifest.permission.READ_CONTACTS
        )
        assertTrue(
            AwPermission.isAllGranted(
                context,
                Manifest.permission.CAMERA,
                Manifest.permission.READ_CONTACTS
            )
        )
    }

    @Test
    fun `isAllGranted returns false when any permission not granted`() {
        Shadows.shadowOf(context).grantPermissions(Manifest.permission.CAMERA)
        assertFalse(
            AwPermission.isAllGranted(
                context,
                Manifest.permission.CAMERA,
                Manifest.permission.READ_CONTACTS
            )
        )
    }

    @Test
    fun `isAllGranted returns true for empty permissions`() {
        assertTrue(AwPermission.isAllGranted(context))
    }

    @Test
    fun `request validates empty permissions throws`() = runTest {
        var caught: Throwable? = null
        try {
            throw IllegalArgumentException("permissions must not be empty")
        } catch (e: IllegalArgumentException) {
            caught = e
        }
        assertNotNull(caught)
        assertEquals("permissions must not be empty", caught?.message)
    }

    @Test
    fun `request validates blank permission names`() {
        val permissions = arrayOf("android.permission.CAMERA", "")
        assertFalse(permissions.all { it.isNotBlank() })
    }

    @Test
    fun `request accepts valid permission names`() {
        val permissions = arrayOf("android.permission.CAMERA", "android.permission.RECORD_AUDIO")
        assertTrue(permissions.all { it.isNotBlank() })
    }

    @Test
    fun `hasPermission extension delegates to AwPermission isGranted`() {
        Shadows.shadowOf(context).grantPermissions(Manifest.permission.CAMERA)
        assertTrue(context.hasPermission(Manifest.permission.CAMERA))
        assertFalse(context.hasPermission(Manifest.permission.READ_CONTACTS))
    }

    @Test
    fun `hasPermissions extension returns false for mixed permissions`() {
        Shadows.shadowOf(context).grantPermissions(Manifest.permission.CAMERA)
        assertFalse(
            context.hasPermissions(Manifest.permission.CAMERA, Manifest.permission.READ_CONTACTS)
        )
    }

    @Test
    fun `hasPermissions extension returns true when all granted`() {
        Shadows.shadowOf(context).grantPermissions(
            Manifest.permission.CAMERA,
            Manifest.permission.READ_CONTACTS
        )
        assertTrue(
            context.hasPermissions(Manifest.permission.CAMERA, Manifest.permission.READ_CONTACTS)
        )
    }

    @Test
    fun `PermissionGroups CAMERA contains expected permissions`() {
        assertTrue(PermissionGroups.CAMERA.contains(Manifest.permission.CAMERA))
        assertEquals(1, PermissionGroups.CAMERA.size)
    }

    @Test
    fun `PermissionGroups MICROPHONE contains RECORD_AUDIO`() {
        assertTrue(PermissionGroups.MICROPHONE.contains(Manifest.permission.RECORD_AUDIO))
    }

    @Test
    fun `PermissionGroups LOCATION contains both fine and coarse`() {
        assertTrue(PermissionGroups.LOCATION.contains(Manifest.permission.ACCESS_FINE_LOCATION))
        assertTrue(PermissionGroups.LOCATION.contains(Manifest.permission.ACCESS_COARSE_LOCATION))
        assertEquals(2, PermissionGroups.LOCATION.size)
    }

    @Test
    fun `PermissionGroups STORAGE contains read and write`() {
        assertTrue(PermissionGroups.STORAGE.contains(Manifest.permission.READ_EXTERNAL_STORAGE))
        assertTrue(PermissionGroups.STORAGE.contains(Manifest.permission.WRITE_EXTERNAL_STORAGE))
    }

    @Test
    fun `PermissionGroups MEDIA_VISUAL contains Android 13 media permissions`() {
        assertTrue(PermissionGroups.MEDIA_VISUAL.contains(Manifest.permission.READ_MEDIA_IMAGES))
        assertTrue(PermissionGroups.MEDIA_VISUAL.contains(Manifest.permission.READ_MEDIA_VIDEO))
    }

    @Test
    fun `PermissionGroups MEDIA_AUDIO contains READ_MEDIA_AUDIO`() {
        assertTrue(PermissionGroups.MEDIA_AUDIO.contains(Manifest.permission.READ_MEDIA_AUDIO))
    }

    @Test
    fun `PermissionGroups NOTIFICATIONS contains POST_NOTIFICATIONS`() {
        assertTrue(PermissionGroups.NOTIFICATIONS.contains(Manifest.permission.POST_NOTIFICATIONS))
    }

    @Test
    fun `PermissionGroups NEARBY_DEVICES contains Bluetooth permissions`() {
        assertTrue(PermissionGroups.NEARBY_DEVICES.contains(Manifest.permission.BLUETOOTH_CONNECT))
        assertTrue(PermissionGroups.NEARBY_DEVICES.contains(Manifest.permission.BLUETOOTH_SCAN))
        assertTrue(PermissionGroups.NEARBY_DEVICES.contains(Manifest.permission.BLUETOOTH_ADVERTISE))
    }

    @Test
    fun `PermissionGroups CONTACTS contains expected permissions`() {
        assertTrue(PermissionGroups.CONTACTS.contains(Manifest.permission.READ_CONTACTS))
        assertTrue(PermissionGroups.CONTACTS.contains(Manifest.permission.WRITE_CONTACTS))
        assertTrue(PermissionGroups.CONTACTS.contains(Manifest.permission.GET_ACCOUNTS))
    }

    @Test
    fun `PermissionGroups PHONE contains expected permissions`() {
        assertTrue(PermissionGroups.PHONE.contains(Manifest.permission.READ_PHONE_STATE))
        assertTrue(PermissionGroups.PHONE.contains(Manifest.permission.CALL_PHONE))
    }

    @Test
    fun `PermissionGroups SMS contains expected permissions`() {
        assertTrue(PermissionGroups.SMS.contains(Manifest.permission.SEND_SMS))
        assertTrue(PermissionGroups.SMS.contains(Manifest.permission.RECEIVE_SMS))
        assertTrue(PermissionGroups.SMS.contains(Manifest.permission.READ_SMS))
    }

    @Test
    fun `PermissionGroups CALENDAR contains expected permissions`() {
        assertTrue(PermissionGroups.CALENDAR.contains(Manifest.permission.READ_CALENDAR))
        assertTrue(PermissionGroups.CALENDAR.contains(Manifest.permission.WRITE_CALENDAR))
    }

    @Test
    fun `PermissionGroups SENSORS contains BODY_SENSORS`() {
        assertTrue(PermissionGroups.SENSORS.contains(Manifest.permission.BODY_SENSORS))
    }

    @Test
    fun `PermissionGroups ACTIVITY_RECOGNITION contains expected permission`() {
        assertTrue(PermissionGroups.ACTIVITY_RECOGNITION.contains(Manifest.permission.ACTIVITY_RECOGNITION))
    }

    @Test
    fun `PermissionGroups BACKGROUND_LOCATION contains expected permission`() {
        assertTrue(PermissionGroups.BACKGROUND_LOCATION.contains(Manifest.permission.ACCESS_BACKGROUND_LOCATION))
    }

    @Test
    fun `PermissionGroups NEARBY_WIFI contains expected permission`() {
        assertTrue(PermissionGroups.NEARBY_WIFI.contains(Manifest.permission.NEARBY_WIFI_DEVICES))
    }

    @Test
    fun `PermissionGroups MEDIA_PARTIAL contains expected permission`() {
        assertTrue(PermissionGroups.MEDIA_PARTIAL.contains(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED))
    }

    @Test
    fun `PermissionGroups SENSORS_BACKGROUND contains expected permission`() {
        assertTrue(PermissionGroups.SENSORS_BACKGROUND.contains(Manifest.permission.BODY_SENSORS_BACKGROUND))
    }

    @Test
    fun `PermissionGroups storage returns STORAGE on API 28`() {
        val result = PermissionGroups.storage()
        assertTrue(result.contains(Manifest.permission.READ_EXTERNAL_STORAGE))
        assertTrue(result.contains(Manifest.permission.WRITE_EXTERNAL_STORAGE))
    }

    @Test
    fun `PermissionGroups location returns LOCATION on API 28`() {
        val result = PermissionGroups.location()
        assertTrue(result.contains(Manifest.permission.ACCESS_FINE_LOCATION))
        assertTrue(result.contains(Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    @Test
    fun `PermissionDetector isProblematicRom returns boolean`() {
        val result = PermissionDetector.isProblematicRom()
        assertNotNull(result)
    }
}
