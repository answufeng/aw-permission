package com.answufeng.permission

import org.junit.Assert.*
import org.junit.Test

/**
 * PermissionResult 数据类的单元测试。
 */
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
    }

    @Test
    fun `isAllGranted returns false when some denied`() {
        val result = PermissionResult(
            granted = listOf("CAMERA"),
            denied = listOf("STORAGE"),
            permanentlyDenied = emptyList()
        )
        assertFalse(result.isAllGranted)
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
    fun `empty result isAllGranted`() {
        val result = PermissionResult(
            granted = emptyList(),
            denied = emptyList(),
            permanentlyDenied = emptyList()
        )
        assertTrue(result.isAllGranted)
    }

    @Test
    fun `equality check`() {
        val a = PermissionResult(listOf("A"), listOf("B"), emptyList())
        val b = PermissionResult(listOf("A"), listOf("B"), emptyList())
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }
}
