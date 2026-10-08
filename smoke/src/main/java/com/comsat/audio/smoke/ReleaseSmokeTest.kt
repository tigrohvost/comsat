package com.comsat.audio.smoke

import android.content.pm.ApplicationInfo
import android.graphics.Rect
import android.net.ConnectivityManager
import android.os.SystemClock
import android.view.KeyEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Configurator
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

// This self-instrumenting test APK drives the installed release via its UI.
// Its classes and dependencies never enter the app's R8 inputs or process.
// Run on a disposable emulator with Wi-Fi and mobile data disabled.
@RunWith(AndroidJUnit4::class)
class ReleaseSmokeTest {
    private val context = InstrumentationRegistry.getInstrumentation().context
    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    @After
    fun restoreDisplay() {
        try {
            screenshot("final")
        } finally {
            device.executeShellCommand("settings put system font_scale 1.0")
            device.executeShellCommand("wm size reset")
            device.executeShellCommand("wm density reset")
            device.setOrientationNatural()
            device.unfreezeRotation()
        }
    }

    @Test
    fun releaseLaunchSelectorsCancellationAndCompactLayout() {
        assertFalse("Smoke test must exercise a release APK",
            context.packageManager.getApplicationInfo(TARGET_PACKAGE, 0).flags and
                ApplicationInfo.FLAG_DEBUGGABLE != 0)
        Configurator.getInstance().waitForIdleTimeout = 0
        device.setOrientationNatural()
        device.executeShellCommand("pm grant $TARGET_PACKAGE android.permission.POST_NOTIFICATIONS")
        disableNetwork()
        launchPanel()
        screenshot("panel-nordic")

        // No source selected: Play should open the selector, not do nothing.
        visible(By.desc("Choose airport")).click()
        visible(By.text("SELECT AIRPORT"))
        visible(By.pkg(TARGET_PACKAGE).clazz("android.widget.EditText")).apply {
            click()
            text = "KJFK"
        }
        verifyTouchTarget("Clear search")
        screenshot("airports-filtered")
        device.pressKeyCode(KeyEvent.KEYCODE_ENTER) // submit search and dismiss keyboard
        visible(By.textContains("John F. Kennedy Intl")).click()
        visible(By.desc("Pause ATC")).click()
        visible(By.desc("Play ATC"))

        visible(By.desc("Choose station")).click()
        visible(By.text("SELECT STATION"))
        visible(By.textContains("Rain Radio"))
        visible(By.text("RETRY")) // directory failure must remain visible with fallback data
        screenshot("stations-offline")
        visible(By.pkg(TARGET_PACKAGE).clazz("android.widget.EditText")).apply {
            click()
            text = "no-such-station"
        }
        visible(By.text("NO MATCHES"))
        visible(By.desc("Clear search")).click()
        device.pressKeyCode(KeyEvent.KEYCODE_ENTER)
        visible(By.textContains("Rain Radio")).click()
        visible(By.textContains("STREAM OFFLINE"))
        visible(By.desc("Pause ambient")).click()
        visible(By.desc("Play ambient"))
        Thread.sleep(6_000) // pass the first reconnect deadline
        assertFalse("Cancelled playback restarted", device.hasObject(By.desc("Pause ambient")))

        // Kill the process as well as its activity; persistence must survive
        // without a retained ViewModel or still-bound foreground service.
        device.executeShellCommand("am force-stop $TARGET_PACKAGE")
        launchPanel()
        visible(By.textContains("KJFK"))
        visible(By.text("RAIN RADIO"))
        verifyFixedPanel()
        selectTheme("LIGHT")
        screenshot("panel-light")
        selectTheme("DARK")
        screenshot("panel-dark")
        selectTheme("NORDIC")
        screenshot("panel-nordic-selected")

        WidgetSmokeScenario(device, ::screenshot).run()

        // Both channels fit without scrolling after rotation and with large fonts.
        device.setOrientationLeft()
        verifyFixedPanel()
        screenshot("landscape-panel")
        device.setOrientationNatural()
        device.executeShellCommand("settings put system font_scale 1.5")
        launchPanel()
        verifyFixedPanel()
        screenshot("large-text-panel")
        device.setOrientationLeft()
        launchPanel()
        assertTrue("Expected landscape with enlarged text",
            device.displayWidth > device.displayHeight &&
                context.resources.configuration.fontScale >= 1.49f)
        verifyFixedPanel()
        screenshot("landscape-large-text-panel")

        // A 320 x 568 dp handset must preserve the same minimum touch targets.
        device.setOrientationNatural()
        device.executeShellCommand("settings put system font_scale 1.0")
        device.executeShellCommand("wm size 640x1136")
        device.executeShellCommand("wm density 320")
        launchPanel()
        assertTrue("Expected a 320 x 568 dp display",
            device.displayWidth == 640 && device.displayHeight == 1136 &&
                context.resources.displayMetrics.density == 2f)
        verifyFixedPanel()
        screenshot("small-panel-320x568")
        assertNull("Emulator reconnected during the offline test", connectivity.activeNetwork)
    }

    private val connectivity: ConnectivityManager
        get() = context.getSystemService(ConnectivityManager::class.java)

    private fun disableNetwork() {
        // Early boot can restore Wi-Fi after the host script disabled it.
        // Enforce isolation again immediately before launching the real APK.
        device.executeShellCommand("cmd connectivity airplane-mode enable")
        device.executeShellCommand("svc wifi disable")
        device.executeShellCommand("svc data disable")
        val deadline = SystemClock.uptimeMillis() + 10_000
        while (connectivity.activeNetwork != null && SystemClock.uptimeMillis() < deadline) {
            Thread.sleep(100)
        }
        assertNull("Offline smoke test requires a disconnected emulator", connectivity.activeNetwork)
    }

    private fun launchPanel() {
        device.executeShellCommand("am start -W -n $TARGET_PACKAGE/.MainActivity -f 0x10008000")
        visible(By.text("COMSAT"))
    }

    private fun visible(selector: BySelector): UiObject2 =
        requireNotNull(device.wait(Until.findObject(selector), 20_000)) {
            "Missing UI element: $selector"
        }

    private fun selectTheme(name: String) {
        visible(By.descStartsWith("Select theme:")).click()
        visible(By.text(name)).click()
        visible(By.desc("Select theme: $name"))
        assertTrue("Theme menu did not close", device.wait(Until.gone(By.text(name)), 5_000))
    }

    private fun verifyFixedPanel() {
        for (description in listOf(
            "Play ATC", "Play ambient", "ATC volume", "ambient volume",
            "Change airport", "Change station"
        )) {
            verifyTouchTarget(description)
        }
        visible(By.textStartsWith("VER "))
        assertFalse("Main panel must fit without scrolling",
            device.hasObject(By.pkg(TARGET_PACKAGE).scrollable(true)))
    }

    private fun verifyTouchTarget(description: String) {
        val minimumSize = (48 * context.resources.displayMetrics.density).toInt() - 1
        val deadline = SystemClock.uptimeMillis() + 5_000
        var bounds: Rect? = null
        do {
            try {
                // Rotation/font changes can replace a node between discovery and measurement.
                // Reacquire it until layout settles; persistent clipping still fails below.
                bounds = device.findObject(By.desc(description))?.visibleBounds
                if (bounds != null && bounds.height() >= minimumSize && bounds.width() >= minimumSize) return
            } catch (_: StaleObjectException) {
                bounds = null
            }
            SystemClock.sleep(50)
        } while (SystemClock.uptimeMillis() < deadline)
        assertTrue("Missing, clipped or undersized control: $description ($bounds)", false)
    }

    private fun screenshot(name: String) {
        // UTP uninstalls test packages after the run, so app-private external
        // files disappear before the host can pull them. Shell-owned captures
        // in Download survive that cleanup.
        val directory = "/sdcard/Download/comsat-smoke"
        device.executeShellCommand("mkdir -p $directory")
        device.executeShellCommand("screencap -p $directory/$name.png")
        val size = device.executeShellCommand("stat -c %s $directory/$name.png")
            .trim().toLongOrNull() ?: 0L
        assertTrue("Could not save screenshot", size > 0L)
        val hierarchy = File(context.getExternalFilesDir(null), "$name.xml")
        device.dumpWindowHierarchy(hierarchy)
        device.executeShellCommand("cp ${hierarchy.absolutePath} $directory/$name.xml")
    }

    private companion object {
        const val TARGET_PACKAGE = "com.comsat.audio"
    }
}
