package com.droiddeck.launcher.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SelfInstallerCallbackTest {
    @Test
    fun statusRequiresTheOutstandingSessionAndToken() {
        val callbacks = SelfInstaller.StatusCallbacks { "unpredictable-token" }
        val token = callbacks.create(41)

        assertFalse(callbacks.accept(42, token, pendingUserAction = false))
        assertFalse(callbacks.accept(41, null, pendingUserAction = false))
        assertFalse(callbacks.accept(41, "attacker-token", pendingUserAction = false))
        assertTrue(callbacks.accept(41, token, pendingUserAction = false))
        assertFalse(callbacks.accept(41, token, pendingUserAction = false))
    }

    @Test
    fun pendingConfirmationAllowsOneFinalStatus() {
        val callbacks = SelfInstaller.StatusCallbacks { "unpredictable-token" }
        val token = callbacks.create(73)

        assertTrue(callbacks.accept(73, token, pendingUserAction = true))
        assertFalse(callbacks.accept(73, token, pendingUserAction = true))
        assertTrue(callbacks.accept(73, token, pendingUserAction = false))
        assertFalse(callbacks.accept(73, token, pendingUserAction = false))
    }

    @Test
    fun cancelledSessionRejectsItsCallback() {
        val callbacks = SelfInstaller.StatusCallbacks { "unpredictable-token" }
        val token = callbacks.create(19)
        callbacks.cancel(19)

        assertFalse(callbacks.accept(19, token, pendingUserAction = false))
    }
}
