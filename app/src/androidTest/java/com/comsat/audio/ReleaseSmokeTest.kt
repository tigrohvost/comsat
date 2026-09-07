package com.comsat.audio

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.view.KeyEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Configurator
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

// Black-box checks: do not reference app classes, so testing does not add R8
// keep rules that could conceal a release-only startup/deserialization bug.
// Run on a disposable emulator with Wi-Fi and mobile data disabled.
@RunWith(AndroidJUnit4::class)
class ReleaseSmokeTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    @After
    fun restoreDisplay() {
        screenshot("final")
        device.executeShellCommand("settings put system font_scale 1.0")
        device.setOrientationNatural()
        device.unfreezeRotation()
    }

    @Test
    fun releaseLaunchSelectorsCancellationAndCompactLayout() {
        assertFalse("Smoke test must exercise a release APK",
            context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0)
        Configurator.getInstance().waitForIdleTimeout = 0
        device.setOrientationNatural()
        device.executeShellCommand("pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS")
        launchPanel()
        screenshot("panel-nordic")

        // No source selected: Play should open the selector, not do nothing.
        visible(By.desc("Choose airport")).click()
        visible(By.text("SELECT AIRPORT"))
        visible(By.desc("ICAO / city / country")).apply {
            click()
            text = "KJFK"
        }
        visible(By.desc("Clear search"))
        device.pressKeyCode(KeyEvent.KEYCODE_ENTER) // submit search and dismiss keyboard
        visible(By.textContains("John F. Kennedy Intl")).click()
        scrollTo(By.desc("Pause ATC"), down = false).click()
        visible(By.desc("Play ATC"))

        scrollTo(By.desc("Choose station")).click()
        visible(By.text("SELECT STATION"))
        visible(By.textContains("Rain Radio"))
        visible(By.text("RETRY")) // directory failure must remain visible with fallback data
        screenshot("stations-offline")
        visible(By.desc("station / genre")).apply {
            click()
            text = "no-such-station"
        }
        visible(By.text("NO MATCHES"))
        visible(By.desc("Clear search")).click()
        device.pressKeyCode(KeyEvent.KEYCODE_ENTER)
        visible(By.textContains("Rain Radio")).click()
        scrollTo(By.desc("Pause ambient")).click()
        visible(By.desc("Play ambient"))
        Thread.sleep(6_000) // pass the first reconnect deadline
        assertFalse("Cancelled playback restarted", device.hasObject(By.desc("Pause ambient")))

        // Recreate the activity and ViewModel; saved selections must survive.
        launchPanel()
        visible(By.textContains("KJFK"))
        scrollTo(By.text("RAIN RADIO"))
        scrollTo(By.desc("Select theme"), down = false).click()
        visible(By.text("LIGHT")).click()
        screenshot("panel-light")
        visible(By.desc("Select theme")).click()
        visible(By.text("DARK")).click()
        screenshot("panel-dark")
        visible(By.desc("Select theme")).click()
        visible(By.text("NORDIC")).click()

        // Both controls remain reachable after rotation and with large fonts.
        device.setOrientationLeft()
        scrollTo(By.desc("Play ATC"), down = false)
        screenshot("landscape-atc")
        scrollTo(By.desc("Play ambient"))
        screenshot("landscape-ambient")
        device.setOrientationNatural()
        device.executeShellCommand("settings put system font_scale 1.5")
        launchPanel()
        scrollTo(By.desc("Play ATC"), down = false)
        screenshot("large-text-atc")
        scrollTo(By.desc("Play ambient"))
        screenshot("large-text-ambient")
    }

    private fun launchPanel() {
        val intent = requireNotNull(context.packageManager.getLaunchIntentForPackage(context.packageName))
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        visible(By.text("COMSAT"))
    }

    private fun visible(selector: BySelector): UiObject2 =
        requireNotNull(device.wait(Until.findObject(selector), 20_000)) {
            "Missing UI element: $selector"
        }

    private fun scrollTo(selector: BySelector, down: Boolean = true): UiObject2 {
        repeat(10) {
            device.wait(Until.findObject(selector), 500)?.let { return it }
            val x = device.displayWidth / 2
            val top = device.displayHeight / 4
            val bottom = device.displayHeight * 3 / 4
            device.swipe(x, if (down) bottom else top, x, if (down) top else bottom, 30)
        }
        return visible(selector)
    }

    private fun screenshot(name: String) {
        val directory = File(context.getExternalFilesDir(null), "smoke").apply { mkdirs() }
        assertTrue("Could not save screenshot", device.takeScreenshot(File(directory, "$name.png")))
    }
}
