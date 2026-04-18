package com.answufeng.permission

import org.junit.Assert.*
import org.junit.Test

class PermissionResultTest {

    @Test
    fun `isAllGranted returns true when all granted`() {
        val result = PermissionResult(
            granted = listOf("CAMERA", "STORAGE"),
            denied = emptyList(),
            permanentlyDenied = emptyList()
        )
        assertTrue(result.isAllGranted)
        assertFalse(result.hasPermanentlyDenied)
        assertFalse(result.hasDenied)
        assertEquals(PermissionResult.Status.Granted, result.status)
    }

    @Test
    fun `isAllGranted returns false when some denied`() {
        val result = PermissionResult(
            granted = listOf("CAMERA"),
            denied = listOf("STORAGE"),
            permanentlyDenied = emptyList()
        )
        assertFalse(result.isAllGranted)
        assertTrue(result.hasDenied)
        assertFalse(result.hasPermanentlyDenied)
        assertEquals(PermissionResult.Status.Denied, result.status)
    }

    @Test
    fun `isAllGranted returns false when some permanently denied`() {
        val result = PermissionResult(
            granted = listOf("CAMERA"),
            denied = emptyList(),
            permanentlyDenied = listOf("LOCATION")
        )
        assertFalse(result.isAllGranted)
        assertTrue(result.hasPermanentlyDenied)
        assertEquals(PermissionResult.Status.PermanentlyDenied, result.status)
    }

    @Test
    fun `hasPermanentlyDenied returns false when none permanently denied`() {
        val result = PermissionResult(
            granted = listOf("CAMERA"),
            denied = listOf("STORAGE"),
            permanentlyDenied = emptyList()
        )
        assertFalse(result.hasPermanentlyDenied)
    }

    @Test
    fun `hasDenied returns true when some denied`() {
        val result = PermissionResult(
            granted = listOf("CAMERA"),
            denied = listOf("STORAGE"),
            permanentlyDenied = emptyList()
        )
        assertTrue(result.hasDenied)
    }

    @Test
    fun `hasDenied returns false when none denied`() {
        val result = PermissionResult(
            granted = listOf("CAMERA"),
            denied = emptyList(),
            permanentlyDenied = listOf("LOCATION")
        )
        assertFalse(result.hasDenied)
    }

    @Test
    fun `firstDenied returns first denied permission`() {
        val result = PermissionResult(
            granted = emptyList(),
            denied = listOf("STORAGE", "CAMERA"),
            permanentlyDenied = emptyList()
        )
        assertEquals("STORAGE", result.firstDenied)
    }

    @Test
    fun `firstDenied returns null when no denied`() {
        val result = PermissionResult(
            granted = listOf("CAMERA"),
            denied = emptyList(),
            permanentlyDenied = emptyList()
        )
        assertNull(result.firstDenied)
    }

    @Test
    fun `firstPermanentlyDenied returns first permanently denied permission`() {
        val result = PermissionResult(
            granted = emptyList(),
            denied = emptyList(),
            permanentlyDenied = listOf("LOCATION", "CAMERA")
        )
        assertEquals("LOCATION", result.firstPermanentlyDenied)
    }

    @Test
    fun `firstPermanentlyDenied returns null when no permanently denied`() {
        val result = PermissionResult(
            granted = listOf("CAMERA"),
            denied = emptyList(),
            permanentlyDenied = emptyList()
        )
        assertNull(result.firstPermanentlyDenied)
    }

    @Test
    fun `copy preserves data correctly`() {
        val original = PermissionResult(
            granted = listOf("A"),
            denied = listOf("B"),
            permanentlyDenied = listOf("C")
        )
        val copy = original.copy(granted = original.granted + listOf("D"))
        assertEquals(2, copy.granted.size)
        assertEquals(1, copy.denied.size)
        assertEquals(1, copy.permanentlyDenied.size)
    }

    @Test
    fun `empty result isAllGranted returns false`() {
        val result = PermissionResult(
            granted = emptyList(),
            denied = emptyList(),
            permanentlyDenied = emptyList()
        )
        assertTrue(result.isEmpty)
        assertFalse(result.isAllGranted)
    }

    @Test
    fun `empty result status is Granted for backward compatibility`() {
        val result = PermissionResult(
            granted = emptyList(),
            denied = emptyList(),
            permanentlyDenied = emptyList()
        )
        assertEquals(PermissionResult.Status.Granted, result.status)
    }

    @Test
    fun `equality check`() {
        val a = PermissionResult(listOf("A"), listOf("B"), emptyList())
        val b = PermissionResult(listOf("A"), listOf("B"), emptyList())
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `inequality check`() {
        val a = PermissionResult(listOf("A"), listOf("B"), emptyList())
        val b = PermissionResult(listOf("A"), emptyList(), listOf("B"))
        assertNotEquals(a, b)
    }

    @Test
    fun `toString contains all fields`() {
        val result = PermissionResult(
            granted = listOf("CAMERA"),
            denied = listOf("STORAGE"),
            permanentlyDenied = listOf("LOCATION")
        )
        val str = result.toString()
        assertTrue(str.contains("CAMERA"))
        assertTrue(str.contains("STORAGE"))
        assertTrue(str.contains("LOCATION"))
        assertTrue(str.contains("PermanentlyDenied"))
    }

    @Test
    fun `status is PermanentlyDenied when both denied and permanentlyDenied exist`() {
        val result = PermissionResult(
            granted = emptyList(),
            denied = listOf("CAMERA"),
            permanentlyDenied = listOf("LOCATION")
        )
        assertEquals(PermissionResult.Status.PermanentlyDenied, result.status)
    }

    @Test
    fun `Status Granted toString`() {
        assertEquals("Granted", PermissionResult.Status.Granted.toString())
    }

    @Test
    fun `Status Denied toString`() {
        assertEquals("Denied", PermissionResult.Status.Denied.toString())
    }

    @Test
    fun `Status PermanentlyDenied toString`() {
        assertEquals("PermanentlyDenied", PermissionResult.Status.PermanentlyDenied.toString())
    }

    @Test
    fun `isGranted returns true for granted permission`() {
        val result = PermissionResult(
            granted = listOf("CAMERA", "STORAGE"),
            denied = listOf("LOCATION"),
            permanentlyDenied = emptyList()
        )
        assertTrue(result.isGranted("CAMERA"))
        assertTrue(result.isGranted("STORAGE"))
    }

    @Test
    fun `isGranted returns false for non-granted permission`() {
        val result = PermissionResult(
            granted = listOf("CAMERA"),
            denied = listOf("LOCATION"),
            permanentlyDenied = emptyList()
        )
        assertFalse(result.isGranted("LOCATION"))
    }

    @Test
    fun `isDenied returns true for denied permission`() {
        val result = PermissionResult(
            granted = listOf("CAMERA"),
            denied = listOf("STORAGE"),
            permanentlyDenied = listOf("LOCATION")
        )
        assertTrue(result.isDenied("STORAGE"))
        assertFalse(result.isDenied("CAMERA"))
        assertFalse(result.isDenied("LOCATION"))
    }

    @Test
    fun `isPermanentlyDenied returns true for permanently denied permission`() {
        val result = PermissionResult(
            granted = listOf("CAMERA"),
            denied = listOf("STORAGE"),
            permanentlyDenied = listOf("LOCATION")
        )
        assertTrue(result.isPermanentlyDenied("LOCATION"))
        assertFalse(result.isPermanentlyDenied("STORAGE"))
        assertFalse(result.isPermanentlyDenied("CAMERA"))
    }

    @Test
    fun `allDenied combines denied and permanentlyDenied`() {
        val result = PermissionResult(
            granted = listOf("CAMERA"),
            denied = listOf("STORAGE"),
            permanentlyDenied = listOf("LOCATION")
        )
        assertEquals(listOf("STORAGE", "LOCATION"), result.allDenied)
    }

    @Test
    fun `allDenied is empty when all granted`() {
        val result = PermissionResult(
            granted = listOf("CAMERA"),
            denied = emptyList(),
            permanentlyDenied = emptyList()
        )
        assertTrue(result.allDenied.isEmpty())
    }
}
