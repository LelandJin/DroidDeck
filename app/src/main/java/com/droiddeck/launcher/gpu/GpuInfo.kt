package com.droiddeck.launcher.gpu

import android.os.Build
import com.droiddeck.launcher.core.FileUtils
import java.io.File

/**
 * What GPU this is, in the terms the driver lists are sorted by. KGSL names the model
 * ("Adreno740v2", "Adreno825"); the family decides which Turnip builds run on it at all, and the
 * support level is what the app has actually been tested on (Adreno 725 and up) - an Adreno 610
 * may start, but it is not "supported" just because it is a Qualcomm chip.
 *
 * Xiaomi 18 Fold's 玄界 O3 (XRING O3) is not an Adreno. Its GPU is a 16-core Arm Mali-G2 Ultra NX
 * on the stock kbase node (/dev/mali0). Turnip talks to KGSL and must not be loaded there; the
 * display compositor uses the system Mali Vulkan driver, and the guest uses a glibc PanVK ICD
 * when one is imported.
 */
data class GpuInfo(
    /** "Adreno 740", or the vendor's own name when this is not an Adreno. */
    val name: String,
    /** The three-digit Adreno model (740, 825), or 0 when KGSL does not say. */
    val model: Int,
    val family: Family,
    /** The SoC as the device reports it ("SM8550", "QCS8550"), or "" when it does not. */
    val soc: String,
    /** Samsung's One UI on an 8 Gen 2: its Turnip needs the OneUI build, or frames tear and flicker. */
    val oneUi8Gen2: Boolean,
) {
    enum class Family(val label: String) {
        A8XX("Adreno 8xx"),
        /** Adreno 710/720/722: gen 7 cores cut down enough to need their own patches. */
        A7XX_LOW("Adreno 710/720/722"),
        A7XX("Adreno 7xx"),
        A6XX("Adreno 6xx"),
        /** An Adreno whose model KGSL does not give: treated as the newest family it could be. */
        ADRENO_UNKNOWN("Adreno"),
        /** Xiaomi 18 Fold: XRING O3, Arm Mali-G2 Ultra NX MP16. Not Turnip. */
        XRING_O3("Mali-G2 Ultra NX"),
        NOT_ADRENO("Not an Adreno GPU"),
    }

    enum class Support { TESTED, UNTESTED, UNSUPPORTED }

    val support: Support
        get() = when {
            family == Family.XRING_O3 -> Support.TESTED
            family == Family.NOT_ADRENO -> Support.UNSUPPORTED
            family == Family.A8XX -> Support.TESTED
            family == Family.A7XX && model >= 725 -> Support.TESTED
            else -> Support.UNTESTED
        }

    /** One line for the device card and the system check. */
    val supportText: String
        get() = when {
            family == Family.XRING_O3 -> "Xiaomi 18 Fold: Mali-G2 system Vulkan. Import PanVK for games."
            support == Support.TESTED -> "Supported"
            support == Support.UNTESTED -> if (family == Family.A7XX_LOW) "Experimental: its drivers are test builds"
                else "Below tested hardware (Adreno 725 and newer): it may not run"
            else -> "Not supported: DroidDeck needs an Adreno (Snapdragon) GPU"
        }

    companion object {
        fun detect(): GpuInfo {
            val adreno = File("/sys/class/kgsl/kgsl-3d0").exists() || File("/vendor/lib64/hw/vulkan.adreno.so").exists()
            val raw = listOf("/sys/class/kgsl/kgsl-3d0/gpu_model", "/sys/class/kgsl/kgsl-3d0/gpu_chipid")
                .firstNotNullOfOrNull { FileUtils.readString(File(it))?.trim()?.takeIf(String::isNotEmpty) }
            val soc = if (Build.VERSION.SDK_INT >= 31) Build.SOC_MODEL.takeIf { it.isNotBlank() && it != Build.UNKNOWN }.orEmpty() else ""
            val model = raw?.let { Regex("""(\d{3})""").find(it)?.groupValues?.get(1)?.toIntOrNull() } ?: 0
            val xring = looksLikeXringO3(Build.HARDWARE, soc, Build.MANUFACTURER, maliPresent())
            val family = familyOf(adreno, model, xring)
            val samsung = Build.MANUFACTURER.equals("samsung", ignoreCase = true)
            return GpuInfo(
                name = when {
                    xring -> "Mali-G2 Ultra NX"
                    !adreno -> Build.HARDWARE.ifBlank { "this GPU" }
                    model > 0 -> "Adreno $model"
                    else -> "Adreno"
                },
                model = model, family = family,
                soc = if (xring && (soc.isBlank() || soc.equals("O3", true) || soc.contains("xring", true))) "XRING O3" else soc,
                oneUi8Gen2 = samsung && model == 740,
            )
        }

        /** Xiaomi 18 Fold reports hardware `xring_o3_asic` and SoC `O3`. */
        internal fun looksLikeXringO3(hardware: String, soc: String, manufacturer: String, mali: Boolean = false): Boolean {
            val h = hardware.lowercase()
            val s = soc.lowercase()
            val blob = "$h $s"
            if (blob.contains("xring_o3") || blob.contains("xringo3") || blob.contains("xring-o3")) return true
            if (h.contains("xring") && (h.contains("o3") || h.contains("asic") || s == "o3" || s.contains("o3"))) return true
            val xiaomi = manufacturer.equals("xiaomi", true) || manufacturer.equals("redmi", true)
            return xiaomi && mali && (s == "o3" || blob.contains("xring"))
        }

        internal fun familyOf(adreno: Boolean, model: Int): Family = familyOf(adreno, model, xringO3 = false)

        internal fun familyOf(adreno: Boolean, model: Int, xringO3: Boolean): Family = when {
            xringO3 -> Family.XRING_O3
            !adreno -> Family.NOT_ADRENO
            model == 0 -> Family.ADRENO_UNKNOWN
            model >= 800 -> Family.A8XX
            model in listOf(710, 720, 722) -> Family.A7XX_LOW
            model >= 700 -> Family.A7XX
            model >= 600 -> Family.A6XX
            else -> Family.ADRENO_UNKNOWN
        }

        private fun maliPresent(): Boolean = listOf(
            "/dev/mali0",
            "/sys/class/misc/mali0",
            "/sys/module/mali_kbase",
            "/vendor/lib64/hw/vulkan.mali.so",
            "/vendor/lib64/egl/libGLES_mali.so",
        ).any { File(it).exists() }
    }
}
