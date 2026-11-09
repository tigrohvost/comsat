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
import androidx.test.uiautomator.Direction
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
        // Media notifications must keep updating even when ordinary notifications
        // were denied. Set the flags before launch so no permission dialog opens.
        device.executeShellCommand("pm revoke $TARGET_PACKAGE android.permission.POST_NOTIFICATIONS")
        device.executeShellCommand("pm set-permission-flags $TARGET_PACKAGE android.permission.POST_NOTIFICATIONS user-set user-fixed")
        disableNetwork()
        launchPanel()
        screenshot("panel-nordic")

        // No source selected: Play should open the selector, not do nothing.
        click(By.desc("Choose airport"))
        visible(By.text("SELECT AIRPORT"))
        visible(By.pkg(TARGET_PACKAGE).clazz("android.widget.EditText")).apply {
            click()
            text = "KJFK"
        }
        verifyTouchTarget("Clear search")
        screenshot("airports-filtered")
        device.pressKeyCode(KeyEvent.KEYCODE_ENTER) // submit search and dismiss keyboard
        click(By.textContains("John F. Kennedy Intl"))
        click(By.desc("Pause ATC"))
        visible(By.desc("Play ATC"))

        click(By.desc("Choose station"))
        visible(By.text("SELECT STATION"))
        visible(By.textContains("Rain Radio"))
        visible(By.text("RETRY")) // directory failure must remain visible with fallback data
        screenshot("stations-offline")
        visible(By.pkg(TARGET_PACKAGE).clazz("android.widget.EditText")).apply {
            click()
            text = "no-such-station"
        }
        visible(By.text("NO MATCHES"))
        click(By.desc("Clear search"))
        device.pressKeyCode(KeyEvent.KEYCODE_ENTER)
        click(By.textContains("Rain Radio"))
        visible(By.textContains("STREAM OFFLINE"))
        click(By.desc("Pause ambient"))
        visible(By.desc("Play ambient"))
        Thread.sleep(6_000) // pass the first reconnect deadline
        assertFalse("Cancelled playback restarted", device.hasObject(By.desc("Pause ambient")))

        click(By.desc("Change airport"))
        visible(By.pkg(TARGET_PACKAGE).clazz("android.widget.EditText")).text = "KJFK"
        device.pressKeyCode(KeyEvent.KEYCODE_ENTER)
        click(By.desc("Favorite airport KJFK"))
        visible(By.text("FAVORITES"))
        click(By.pkg(TARGET_PACKAGE).desc("Back"))
        click(By.desc("Change station"))
        click(By.desc("Favorite station rain-radio"))
        visible(By.text("FAVORITES"))
        click(By.pkg(TARGET_PACKAGE).desc("Back"))
        openUtility("SAVED MIXES")
        visible(By.pkg(TARGET_PACKAGE).clazz("android.widget.EditText")).text = "Smoke mix"
        device.pressKeyCode(KeyEvent.KEYCODE_ENTER)
        click(By.text("SAVE CURRENT MIX"))
        visible(By.text("Smoke mix"))
        click(By.pkg(TARGET_PACKAGE).desc("Back"))
        openUtility("SETTINGS / SLEEP")
        setting("Animate background map").click()
        setting("Lower music during ATC").click()
        click(By.pkg(TARGET_PACKAGE).desc("Back"))

        // Kill the process as well as its activity; persistence must survive
        // without a retained ViewModel or still-bound foreground service.
        device.executeShellCommand("am force-stop $TARGET_PACKAGE")
        launchPanel()
        visible(By.textContains("KJFK"))
        visible(By.text("RAIN RADIO"))
        verifySavedMixAndTimer()
        verifyFixedPanel()
        selectTheme("LIGHT")
        screenshot("panel-light")
        selectTheme("DARK")
        screenshot("panel-dark")
        selectTheme("ORANGE")
        screenshot("panel-orange")
        device.executeShellCommand("am force-stop $TARGET_PACKAGE")
        launchPanel()
        visible(By.desc("Select theme: ORANGE"))
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
        click(By.descStartsWith("Select theme:"))
        click(By.text(name))
        visible(By.desc("Select theme: $name"))
        assertTrue("Theme menu did not close", device.wait(Until.gone(By.text(name)), 5_000))
    }

    private fun click(selector: BySelector) {
        SystemClock.sleep(750) // destination transitions can expose nodes before accepting touches
        repeat(4) {
            try {
                visible(selector).click()
                return
            } catch (_: StaleObjectException) {
                SystemClock.sleep(100)
            }
        }
        visible(selector).click()
    }

    private fun openUtility(title: String) {
        click(By.descStartsWith("Select theme:"))
        click(By.text(title))
    }

    private fun verifySavedMixAndTimer() {
        click(By.desc("Change airport"))
        visible(By.text("FAVORITES"))
        visible(By.desc("Favorite airport KJFK"))
        click(By.pkg(TARGET_PACKAGE).desc("Back"))
        click(By.desc("Change station"))
        visible(By.text("FAVORITES"))
        click(By.pkg(TARGET_PACKAGE).desc("Back"))
        openUtility("SETTINGS / SLEEP")
        assertFalse("Map preference was lost", setting("Animate background map").isChecked)
        assertTrue("Ducking preference was lost", setting("Lower music during ATC").isChecked)
        click(By.pkg(TARGET_PACKAGE).desc("Back"))
        verifyMapMotion()
        openUtility("SAVED MIXES")
        click(By.text("Smoke mix"))
        visible(By.desc("Pause ambient"))
        visible(By.desc("Pause ATC"))
        openUtility("SETTINGS / SLEEP")
        click(By.text("15 MIN"))
        visible(By.textStartsWith("SLP 14:"))
        assertTrue("Notification permission must be denied in this scenario",
            context.packageManager.checkPermission(android.Manifest.permission.POST_NOTIFICATIONS, TARGET_PACKAGE) ==
                android.content.pm.PackageManager.PERMISSION_DENIED)
        val firstNotification = sleepNotificationText()
        SystemClock.sleep(1_500)
        assertTrue("Sleep countdown stopped updating in the media notification",
            firstNotification != sleepNotificationText())
        click(By.text("CANCEL TIMER"))
        visible(By.text("TIMER OFF"))
        click(By.text("30 MIN"))
        click(By.pkg(TARGET_PACKAGE).desc("Back"))
        click(By.desc("Pause ATC"))
        click(By.desc("Pause ambient"))
        assertTrue("Manual stop left the sleep timer armed",
            device.wait(Until.gone(By.textStartsWith("SLP ")), 5_000))
        openUtility("SAVED MIXES")
        click(By.text("Smoke mix"))
        click(By.desc("Retry ambient now"))
        visible(By.textContains("STREAM OFFLINE"))
        openUtility("SETTINGS / SLEEP")
        visible(By.pkg(TARGET_PACKAGE).clazz("android.widget.EditText")).text = "1"
        device.pressKeyCode(KeyEvent.KEYCODE_ENTER)
        click(By.text("START TIMER"))
        click(By.pkg(TARGET_PACKAGE).desc("Back"))
        visible(By.textStartsWith("SLP "))
        screenshot("sleep-timer")
        device.pressHome()
        device.sleep()
        Thread.sleep(65_000)
        device.wakeUp()
        device.executeShellCommand("wm dismiss-keyguard")
        launchPanel()
        visible(By.desc("Play ATC"))
        visible(By.desc("Play ambient"))
        assertFalse("Expired sleep timer still displayed", device.hasObject(By.textStartsWith("SLP ")))
        assertFalse("Expired timer left a media notification",
            device.executeShellCommand("dumpsys notification --noredact")
                .contains("pkg=$TARGET_PACKAGE user=UserHandle{0} id=1001"))
        openUtility("SAVED MIXES")
        click(By.desc("Delete mix Smoke mix"))
        visible(By.text("NO SAVED MIXES"))
        click(By.pkg(TARGET_PACKAGE).desc("Back"))
    }

    private fun verifyMapMotion() {
        val previousScale = device.executeShellCommand("settings get global animator_duration_scale").trim()
        try {
            captureFrames("map-static")
            openUtility("SETTINGS / SLEEP")
            setting("Animate background map").click()
            click(By.pkg(TARGET_PACKAGE).desc("Back"))
            captureFrames("map-animated")
            device.executeShellCommand("settings put global animator_duration_scale 0")
            captureFrames("map-system-disabled")
        } finally {
            if (previousScale == "null") device.executeShellCommand("settings delete global animator_duration_scale")
            else device.executeShellCommand("settings put global animator_duration_scale $previousScale")
        }
        openUtility("SETTINGS / SLEEP")
        setting("Animate background map").click()
        click(By.pkg(TARGET_PACKAGE).desc("Back"))
    }

    private fun captureFrames(name: String) {
        SystemClock.sleep(750) // finish the navigation transition before sampling
        device.executeShellCommand("dumpsys gfxinfo $TARGET_PACKAGE reset")
        Thread.sleep(5_000)
        val report = File(context.getExternalFilesDir(null), "$name-gfxinfo.txt")
        report.writeText(device.executeShellCommand("dumpsys gfxinfo $TARGET_PACKAGE"))
        device.executeShellCommand("cp ${report.absolutePath} /sdcard/Download/comsat-smoke/${report.name}")
    }

    private fun sleepNotificationText(): String {
        val key = device.executeShellCommand("cmd notification list").lineSequence()
            .map(String::trim).firstOrNull { it.contains("|$TARGET_PACKAGE|1001|") }
            ?: error("Missing COMSAT media notification")
        // UiAutomation executes argv directly, rather than through a shell.
        return device.executeShellCommand("cmd notification get $key").lineSequence()
            .firstOrNull { it.contains("android.text") && it.contains("SLP ") }
            ?: error("Sleep countdown missing from media notification")
    }

    private fun setting(title: String): UiObject2 {
        repeat(5) {
            device.findObject(By.text(title))?.let { item ->
                if (!item.visibleBounds.isEmpty) {
                    var toggle: UiObject2? = item
                    while (toggle != null && !toggle.isCheckable) toggle = toggle.parent
                    return toggle ?: item
                }
            }
            device.findObject(By.pkg(TARGET_PACKAGE).scrollable(true))?.scroll(Direction.DOWN, 0.6f)
        }
        return visible(By.text(title))
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
        SystemClock.sleep(750) // capture the settled destination, after its fade
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
