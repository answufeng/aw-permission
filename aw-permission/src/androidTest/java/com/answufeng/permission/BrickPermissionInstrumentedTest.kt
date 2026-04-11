package com.answufeng.permission

import android.Manifest
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

class TestPermissionActivity : FragmentActivity()

@RunWith(AndroidJUnit4::class)
class BrickPermissionInstrumentedTest {

    @Test
    fun request_returnsGrantedResult_forAlreadyGrantedPermission() {
        val scenario = ActivityScenario.launch(TestPermissionActivity::class.java)
        val latch = CountDownLatch(1)
        var result: PermissionResult? = null

        scenario.onActivity { activity ->
            activity.lifecycleScope.launch {
                result = BrickPermission.request(activity, Manifest.permission.INTERNET)
                latch.countDown()
            }
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS))
        assertNotNull(result)
        assertTrue(result!!.isAllGranted)
        assertEquals(listOf(Manifest.permission.INTERNET), result!!.granted)
        scenario.close()
    }

    @Test
    fun request_rejectsEmptyPermissions_onDevice() {
        val scenario = ActivityScenario.launch(TestPermissionActivity::class.java)
        val latch = CountDownLatch(1)
        var thrown: Throwable? = null

        scenario.onActivity { activity ->
            activity.lifecycleScope.launch {
                try {
                    BrickPermission.request(activity)
                } catch (t: Throwable) {
                    thrown = t
                } finally {
                    latch.countDown()
                }
            }
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS))
        assertTrue(thrown is IllegalArgumentException)
        assertEquals("permissions must not be empty", thrown?.message)
        scenario.close()
    }
}