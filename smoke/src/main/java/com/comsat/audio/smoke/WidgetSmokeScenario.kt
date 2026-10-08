package com.comsat.audio.smoke

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import java.util.regex.Pattern

internal class WidgetSmokeScenario(
    private val device: UiDevice,
    private val screenshot: (String) -> Unit
) {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.context
    private val manager = AppWidgetManager.getInstance(context)
    private val allocator = AppWidgetHost(context, WidgetHostActivity.HOST_ID)
    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var activity: WidgetHostActivity? = null

    // The main smoke scenario has already selected KJFK and Rain Radio, then stopped both.
    fun run() {
        // API 35's appwidget shell command forwards USER_CURRENT (-2) without
        // resolving it, so the service rejects that otherwise documented flag.
        val userId = device.executeShellCommand("am get-current-user").trim().toInt()
        device.executeShellCommand("appwidget grantbind --package ${context.packageName} --user $userId")
        try {
            widgetId = allocator.allocateAppWidgetId()
            assertTrue("Could not bind release widget", manager.bindAppWidgetIdIfAllowed(
                widgetId,
                ComponentName(TARGET_PACKAGE, "$TARGET_PACKAGE.widget.ComsatWidgetProvider"),
                Bundle().apply {
                    putInt(AppWidgetManager.OPTION_APPWIDGET_HOST_CATEGORY,
                        AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN)
                }
            ))
            showHost()
            verifySources("KJFK")
            verifyStopped()
            verifyLayout()
            screenshot("widget-compact")
            val nordicTextColor = widgetTextColor()
            for (theme in listOf("LIGHT", "DARK", "NORDIC")) {
                verifyTheme(theme)
            }
            assertEquals("Nordic widget palette was not restored", nordicTextColor, widgetTextColor())
            showHost()

            // Tapping the actual RemoteViews PendingIntent must open the real app.
            visible(widget("widget_panel")).click()
            visible(By.text("COMSAT"))
            visible(By.desc("Change airport")).click()
            visible(By.pkg(TARGET_PACKAGE).clazz("android.widget.EditText")).apply {
                click()
                text = "KLAX"
            }
            device.pressKeyCode(KeyEvent.KEYCODE_ENTER)
            visible(By.textContains("Los Angeles Intl")).click()
            visible(By.desc("Pause ATC")).click()
            visible(By.desc("Play ATC"))
            showHost()
            verifySources("KLAX")
            verifyStopped()

            openApp()
            visible(By.desc("Play ambient")).click()
            visible(By.textContains("STREAM OFFLINE"))
            showHost()
            visible(widget("widget_station_status").text(Pattern.compile("RECONNECTING|OFFLINE")))
            screenshot("widget-reconnecting")
            openApp()
            visible(By.desc("Pause ambient")).click()
            visible(By.desc("Play ambient"))
            showHost()
            verifyStopped()

            // Recreate the host without allocating a replacement widget ID.
            val monitor = instrumentation.addMonitor(WidgetHostActivity::class.java.name, null, false)
            try {
                instrumentation.runOnMainSync { requireNotNull(activity).recreate() }
                activity = requireNotNull(monitor.waitForActivityWithTimeout(10_000)) as WidgetHostActivity
            } finally {
                instrumentation.removeMonitor(monitor)
            }
            verifySources("KLAX")
            verifyStopped()

            // Force-stop suppresses Android widget delivery until the app is launched explicitly.
            device.executeShellCommand("am force-stop $TARGET_PACKAGE")
            openApp()
            visible(By.textContains("KLAX"))
            visible(By.text("RAIN RADIO"))
            showHost()
            verifySources("KLAX")
            verifyStopped()
            instrumentation.runOnMainSync { requireNotNull(activity).resizeWidget(320, 200) }
            verifyLayout()
            visible(widget("widget_airport_detail").text("Los Angeles Intl"))
            visible(widget("widget_station_detail").text("RAIN NET"))
            screenshot("widget-wide-relaunched")

            device.executeShellCommand("settings put system font_scale 1.5")
            showHost(width = 180, height = 140)
            verifySources("KLAX")
            verifyStopped()
            assertTrue("Widget must use enlarged text",
                requireNotNull(activity).resources.configuration.fontScale >= 1.49f)
            verifyLayout()
            screenshot("widget-minimum-large-text")
        } catch (failure: Throwable) {
            try {
                screenshot("widget-failure")
            } catch (captureFailure: Exception) {
                failure.addSuppressed(captureFailure)
            }
            throw failure
        } finally {
            instrumentation.runOnMainSync { activity?.finish() }
            instrumentation.waitForIdleSync()
            if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                allocator.deleteAppWidgetId(widgetId)
            }
            device.executeShellCommand("appwidget revokebind --package ${context.packageName} --user $userId")
            device.executeShellCommand("settings put system font_scale 1.0")
            openApp()
        }
    }

    private fun showHost(width: Int = 200, height: Int = 160) {
        activity = instrumentation.startActivitySync(Intent(context, WidgetHostActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            putExtra(WidgetHostActivity.EXTRA_WIDTH, width)
            putExtra(WidgetHostActivity.EXTRA_HEIGHT, height)
        }) as WidgetHostActivity
        visible(widget("widget_panel"))
    }

    private fun openApp() {
        device.executeShellCommand("am start -W -n $TARGET_PACKAGE/.MainActivity -f 0x10008000")
        visible(By.text("COMSAT"))
    }

    private fun verifySources(airport: String) {
        visible(widget("widget_airport_source").text(airport))
        visible(widget("widget_station_source").text("Rain Radio"))
    }

    private fun verifyStopped() {
        visible(widget("widget_airport_status").text("STOPPED"))
        visible(widget("widget_station_status").text("STOPPED"))
    }

    private fun verifyTheme(name: String) {
        val previousColor = widgetTextColor()
        visible(widget("widget_panel")).click()
        visible(By.descStartsWith("Select theme:")).click()
        visible(By.text(name)).click()
        visible(By.desc("Select theme: $name"))
        assertTrue("Theme menu did not close", device.wait(Until.gone(By.text(name)), 5_000))
        showHost(width = 320, height = 200)
        verifySources("KJFK")
        val deadline = SystemClock.uptimeMillis() + 5_000
        while (widgetTextColor() == previousColor && SystemClock.uptimeMillis() < deadline) {
            SystemClock.sleep(50)
        }
        assertTrue("Widget did not apply the $name palette", widgetTextColor() != previousColor)
        verifyLayout()
        visible(widget("widget_airport_detail").text("John F. Kennedy Intl"))
        visible(widget("widget_station_detail").text("RAIN NET"))
        screenshot("widget-${name.lowercase()}")
    }

    private fun widgetTextColor(): Int {
        var color = 0
        instrumentation.runOnMainSync {
            val resources = context.packageManager.getResourcesForApplication(TARGET_PACKAGE)
            val id = resources.getIdentifier("widget_station_source", "id", TARGET_PACKAGE)
            color = requireNotNull(activity).widgetView.findViewById<TextView>(id).currentTextColor
        }
        return color
    }

    private fun verifyLayout() {
        verifyStopped()
        instrumentation.waitForIdleSync()
        instrumentation.runOnMainSync {
            val current = requireNotNull(activity)
            val providerResources = context.packageManager.getResourcesForApplication(TARGET_PACKAGE)
            val hostBounds = Rect()
            assertTrue(current.widgetView.getGlobalVisibleRect(hostBounds))
            for (name in listOf("widget_airport_source", "widget_station_source",
                "widget_airport_status", "widget_station_status")) {
                val id = providerResources.getIdentifier(name, "id", TARGET_PACKAGE)
                val text = requireNotNull(current.widgetView.findViewById<TextView>(id)) { "Missing $name" }
                val bounds = Rect()
                assertTrue("Hidden widget field: $name", text.getGlobalVisibleRect(bounds))
                assertTrue("Widget field outside host: $name", hostBounds.contains(bounds))
                assertEquals("Horizontally clipped widget field: $name", text.width, bounds.width())
                assertEquals("Clipped widget field: $name", text.height, bounds.height())
                val layout = requireNotNull(text.layout)
                assertTrue("Vertically clipped widget text: $name",
                    layout.getLineBottom(layout.lineCount - 1) <= text.height - text.totalPaddingTop - text.totalPaddingBottom)
                for (line in 0 until layout.lineCount) {
                    assertEquals("Truncated widget text: $name", 0, layout.getEllipsisCount(line))
                }
            }
        }
    }

    private fun widget(name: String) = By.res(TARGET_PACKAGE, name)

    private fun visible(selector: BySelector): UiObject2 =
        requireNotNull(device.wait(Until.findObject(selector), 20_000)) { "Missing widget UI: $selector" }

    private companion object {
        const val TARGET_PACKAGE = "com.comsat.audio"
    }
}
