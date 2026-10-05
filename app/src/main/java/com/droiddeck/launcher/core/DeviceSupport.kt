package com.droiddeck.launcher.core

import android.os.Build
import com.droiddeck.launcher.gpu.GpuInfo
import java.io.File

/**
 * Whether this device can draw a Linux session at all.
 *
 * On Adreno the runtime draws with Turnip. Xiaomi 18 Fold (XRING O3, Mali-G2 Ultra NX) does not
 * have KGSL; that branch drives the display with the system Mali Vulkan driver and the guest with
 * PanVK against /dev/mali0, so it is allowed through the same gate. Other Mali, Xclipse and
 * PowerVR devices still are not: the compositor gets no usable Vulkan device and a session comes
 * up as sound over a black screen.
 */
object DeviceSupport {
    fun adreno(): Boolean =
        File("/sys/class/kgsl/kgsl-3d0").exists() || File("/vendor/lib64/hw/vulkan.adreno.so").exists()

    fun xringO3(): Boolean = GpuInfo.detect().family == GpuInfo.Family.XRING_O3

    /** Adreno, or the Xiaomi 18 Fold SoC this branch knows how to drive without Turnip. */
    fun canHostSession(): Boolean = adreno() || xringO3()

    /** The chip as the device names it, for the card that explains the refusal. */
    fun gpuName(): String {
        val soc = if (Build.VERSION.SDK_INT >= 31) Build.SOC_MODEL.takeIf { it.isNotBlank() && it != Build.UNKNOWN } else null
        return soc?.let { "$it (${Build.HARDWARE})" } ?: Build.HARDWARE.ifBlank { "this GPU" }
    }
}
