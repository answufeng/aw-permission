package com.answufeng.permission

import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
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
 *   the pending continuation and schedules removal via [scheduleRemoval].
 * - [onDestroy] serves as a fallback to cancel any still-pending continuation.
 *
 * ### Timeout Protection
 * - [requestPermissions] uses [withTimeoutOrNull] with a 60-second timeout.
 *   If the system permission dialog is abnormally closed (rare on some ROMs),
 *   the coroutine will not hang indefinitely. Timed-out permissions are marked as denied.
 *
 * ### Thread Safety
 * - The continuation is stored in an [AtomicReference] to ensure thread-safe access
 *   from both the UI thread (where the launcher callback runs) and any other thread
 *   (where cancellation might occur).
 * - [AtomicLong] is used for tag generation to guarantee unique Fragment tags.
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
            scheduleRemoval()
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
     * Includes a 60-second timeout. If the permission dialog is abnormally closed
     * (e.g., on some custom ROMs), all permissions are marked as denied instead of
     * hanging the coroutine indefinitely.
     *
     * @param permissions The permissions to request
     * @return A map of permission names to their grant status
     */
    internal suspend fun requestPermissions(permissions: Array<String>): Map<String, Boolean> {
        return withTimeoutOrNull(REQUEST_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                continuationRef.set(cont)
                cont.invokeOnCancellation { continuationRef.compareAndSet(cont, null) }
                try {
                    permissionLauncher.launch(permissions)
                } catch (e: IllegalStateException) {
                    continuationRef.getAndSet(null)
                    cont.cancel(e)
                }
            }
        } ?: permissions.associateWith { false }
    }

    private fun removeFromParent() {
        if (isAdded) {
            parentFragmentManager.beginTransaction().remove(this).commitAllowingStateLoss()
        }
    }

    /**
     * Schedules Fragment removal, handling the case where the Fragment is not yet added.
     *
     * After a configuration change, the Fragment may be restored before it is attached
     * to the Activity. In this case, [commitAllowingStateLoss] would silently fail.
     * We use a [DefaultLifecycleObserver] to defer removal until [onResume].
     */
    private fun scheduleRemoval() {
        if (isAdded) {
            removeFromParent()
        } else {
            lifecycle.addObserver(object : androidx.lifecycle.DefaultLifecycleObserver {
                override fun onResume(owner: androidx.lifecycle.LifecycleOwner) {
                    removeFromParent()
                    lifecycle.removeObserver(this)
                }
            })
        }
    }

    internal companion object {
        private const val REQUEST_TIMEOUT_MS = 60_000L

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
