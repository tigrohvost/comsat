package com.comsat.audio.smoke

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.FrameLayout
import kotlin.math.roundToInt

// A real Android widget host confined to the self-instrumenting smoke APK.
// The provider and RemoteViews still come from the installed, optimized app.
class WidgetHostActivity : Activity() {
    private lateinit var host: AppWidgetHost
    lateinit var widgetView: AppWidgetHostView
        private set
    private var widthDp = 200
    private var heightDp = 160

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        widthDp = savedInstanceState?.getInt(EXTRA_WIDTH) ?: intent.getIntExtra(EXTRA_WIDTH, 200)
        heightDp = savedInstanceState?.getInt(EXTRA_HEIGHT) ?: intent.getIntExtra(EXTRA_HEIGHT, 160)
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
        val provider = AppWidgetManager.getInstance(this).getAppWidgetInfo(widgetId)
        if (provider == null) {
            // A font-scale update can recreate a stopped host after test cleanup
            // has deleted its widget ID. There is no widget left to display.
            finish()
            return
        }
        host = AppWidgetHost(this, HOST_ID)
        widgetView = host.createView(this, widgetId, provider)
        setContentView(FrameLayout(this).apply {
            setBackgroundColor(Color.rgb(34, 44, 52))
            addView(widgetView, FrameLayout.LayoutParams(1, 1, Gravity.CENTER))
        })
        resizeWidget(widthDp, heightDp)
    }

    fun resizeWidget(width: Int, height: Int) {
        widthDp = width
        heightDp = height
        val density = resources.displayMetrics.density
        // The provider's minimum dimensions describe its content. A launcher
        // adds the platform host padding around that space.
        val outerWidth = (width * density).roundToInt() + widgetView.paddingLeft + widgetView.paddingRight
        val outerHeight = (height * density).roundToInt() + widgetView.paddingTop + widgetView.paddingBottom
        widgetView.layoutParams = FrameLayout.LayoutParams(
            outerWidth,
            outerHeight,
            Gravity.CENTER
        )
        // This performs the same options update and provider callback as launcher resizing.
        // updateAppWidgetSize subtracts the default host padding from these outer bounds.
        @Suppress("DEPRECATION")
        widgetView.updateAppWidgetSize(Bundle(),
            (outerWidth / density).roundToInt(), (outerHeight / density).roundToInt(),
            (outerWidth / density).roundToInt(), (outerHeight / density).roundToInt())
    }

    override fun onStart() {
        super.onStart()
        if (::host.isInitialized) host.startListening()
    }

    override fun onStop() {
        if (::host.isInitialized) host.stopListening()
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(EXTRA_WIDTH, widthDp)
        outState.putInt(EXTRA_HEIGHT, heightDp)
        super.onSaveInstanceState(outState)
    }

    companion object {
        const val HOST_ID = 7319
        const val EXTRA_WIDTH = "widget_width"
        const val EXTRA_HEIGHT = "widget_height"
    }
}
