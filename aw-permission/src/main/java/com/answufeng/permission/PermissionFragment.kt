package com.answufeng.permission

import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume

/**
 * Headless Fragment that acts as a proxy for permission requests.
 *
 * Each permission request creates a new instance of this Fragment. The Fragment uses
 * [ActivityResultContracts.RequestMultiplePermissions] to launch the permission dialog,
 * and suspends the calling coroutine via [suspendCancellableCoroutine] until the user
 * responds.
 *
 * ### Lifecycle
 * - Created and added to the Activity via [create] at the start of each request.
 * - Automatically removed from the Activity after the request completes.
 * - If the Fragment is restored from a saved state (configuration change), it cancels
 *   the pending continuation and removes itself, since the original coroutine context
 *   is no longer valid.
 * - [onDestroy] serves as a fallback to cancel any still-pending continuation.
 *
 * ### Thread Safety
 * - The continuation is stored in an [AtomicReference] to ensure thread-safe access
 *   from both the UI thread (where the launcher callback runs) and any other thread
 *   (where cancellation might occur).
 * - [AtomicLong] is used for tag generation to guarantee unique Fragment tags without
 *   the theoretical collision risk of [System.nanoTime].
 *
 * This class is internal and managed by [AwPermission].
 */
internal class PermissionFragment : Fragment() {

    private val continuationRef = AtomicReference<CancellableContinuation<Map<String, Boolean>>?>(null)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { resultMap ->
        val cont = continuationRef.getAndSet(null)
        cont?.resume(resultMap)
        removeFromParent()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) {
            val cont = continuationRef.getAndSet(null)
            cont?.cancel()
            removeFromParent()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        val cont = continuationRef.getAndSet(null)
        cont?.cancel()
    }

    /**
     * Launches the permission request and suspends until the user responds.
     *
     * @param permissions The permissions to request
     * @return A map of permission names to their grant status
     */
    internal suspend fun requestPermissions(permissions: Array<String>): Map<String, Boolean> {
        return suspendCancellableCoroutine { cont ->
            continuationRef.set(cont)
            cont.invokeOnCancellation { continuationRef.compareAndSet(cont, null) }
            try {
                permissionLauncher.launch(permissions)
            } catch (e: IllegalStateException) {
                continuationRef.getAndSet(null)
                cont.cancel(e)
            }
        }
    }

    private fun removeFromParent() {
        if (isAdded) {
            parentFragmentManager.beginTransaction().remove(this).commitAllowingStateLoss()
        }
    }

    internal companion object {
        internal fun create(activity: FragmentActivity): PermissionFragment {
            val fm = activity.supportFragmentManager
            val tag = "AwPermissionFragment_${AwPermission.tagCounter.incrementAndGet()}"
            val fragment = PermissionFragment()
            fm.beginTransaction()
                .add(fragment, tag)
                .commitNowAllowingStateLoss()
            return fragment
        }
    }
}
