package com.byron.trucaller.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.byron.trucaller.TruCallerApplication
import com.byron.trucaller.data.model.CallerIdEntry
import com.byron.trucaller.data.model.SpamCategory
import kotlinx.coroutines.runBlocking

class CallerIdOverlayService : Service() {

    companion object {
        private const val TAG = "CallerIdOverlay"
        private const val EXTRA_PHONE_NUMBER = "extra_phone_number"

        // Spam category colors
        private const val COLOR_SAFE = 0xFF2E7D32.toInt()
        private const val COLOR_SUSPECTED_SPAM = 0xFFFFCD00.toInt()
        private const val COLOR_SPAM = 0xFFFF9800.toInt()
        private const val COLOR_FRAUD = 0xFFD90000.toInt()

        fun show(context: Context, phoneNumber: String) {
            val intent = Intent(context, CallerIdOverlayService::class.java).apply {
                putExtra(EXTRA_PHONE_NUMBER, phoneNumber)
            }
            context.startService(intent)
        }

        fun dismiss(context: Context) {
            val intent = Intent(context, CallerIdOverlayService::class.java)
            context.stopService(intent)
        }
    }

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val phoneNumber = intent?.getStringExtra(EXTRA_PHONE_NUMBER)
        if (phoneNumber.isNullOrBlank()) {
            stopSelf()
            return START_NOT_STICKY
        }

        // Remove any existing overlay before showing a new one
        removeOverlay()

        // Look up the number
        val app = applicationContext as? TruCallerApplication
        if (app == null) {
            Log.e(TAG, "Failed to get TruCallerApplication")
            stopSelf()
            return START_NOT_STICKY
        }

        val callerIdRepo = app.container.callerIdRepository
        val lookupResult = try {
            runBlocking { callerIdRepo.lookupNumber(phoneNumber) }
        } catch (e: Exception) {
            Log.e(TAG, "Error looking up number: $phoneNumber", e)
            null
        }

        val entry = lookupResult?.callerIdEntry
        showOverlay(phoneNumber, entry)

        return START_NOT_STICKY
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showOverlay(phoneNumber: String, entry: CallerIdEntry?) {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dpToPx(80)
        }

        val callerName = entry?.name ?: "Unknown Caller"
        val spamScore = entry?.spamScore ?: -1
        val category = entry?.category ?: SpamCategory.SAFE

        overlayView = buildOverlayView(callerName, phoneNumber, spamScore, category)

        // Make the overlay draggable
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        overlayView?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    layoutParams.x = initialX + (event.rawX - initialTouchX).toInt()
                    layoutParams.y = initialY + (event.rawY - initialTouchY).toInt()
                    try {
                        windowManager?.updateViewLayout(overlayView, layoutParams)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error updating overlay position", e)
                    }
                    true
                }
                else -> false
            }
        }

        try {
            windowManager?.addView(overlayView, layoutParams)
        } catch (e: Exception) {
            Log.e(TAG, "Error adding overlay view", e)
            stopSelf()
        }
    }

    private fun buildOverlayView(
        callerName: String,
        phoneNumber: String,
        spamScore: Int,
        category: SpamCategory
    ): View {
        val categoryColor = when (category) {
            SpamCategory.SAFE -> COLOR_SAFE
            SpamCategory.SUSPECTED_SPAM -> COLOR_SUSPECTED_SPAM
            SpamCategory.SPAM -> COLOR_SPAM
            SpamCategory.FRAUD -> COLOR_FRAUD
        }

        val categoryLabel = when (category) {
            SpamCategory.SAFE -> "Safe"
            SpamCategory.SUSPECTED_SPAM -> "Suspected Spam"
            SpamCategory.SPAM -> "Spam"
            SpamCategory.FRAUD -> "Fraud"
        }

        // Card background
        val cardBackground = GradientDrawable().apply {
            setColor(0xFF1E1E1E.toInt())
            cornerRadius = dpToPx(16).toFloat()
            setStroke(dpToPx(2), categoryColor)
        }

        // Badge background
        val badgeBackground = GradientDrawable().apply {
            setColor(categoryColor)
            cornerRadius = dpToPx(12).toFloat()
        }

        // Spam score badge background
        val scoreBadgeBackground = GradientDrawable().apply {
            setColor(0xFF2A2A2A.toInt())
            cornerRadius = dpToPx(8).toFloat()
            setStroke(dpToPx(1), categoryColor)
        }

        // Dismiss button background
        val dismissBackground = GradientDrawable().apply {
            setColor(0xFF333333.toInt())
            cornerRadius = dpToPx(12).toFloat()
        }

        // Root card container
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = cardBackground
            setPadding(dpToPx(16), dpToPx(12), dpToPx(16), dpToPx(12))
            minimumWidth = dpToPx(280)
            elevation = dpToPx(8).toFloat()
        }

        // Top row: category badge + dismiss button
        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val categoryBadge = TextView(this).apply {
            text = categoryLabel
            setTextColor(
                if (category == SpamCategory.SUSPECTED_SPAM) 0xFF1A1A1A.toInt()
                else Color.WHITE
            )
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.DEFAULT_BOLD
            background = badgeBackground
            setPadding(dpToPx(10), dpToPx(4), dpToPx(10), dpToPx(4))
        }

        val spacer = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, 0, 1f)
        }

        val dismissButton = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            setColorFilter(0xFFB0B0B0.toInt())
            background = dismissBackground
            setPadding(dpToPx(6), dpToPx(6), dpToPx(6), dpToPx(6))
            layoutParams = LinearLayout.LayoutParams(dpToPx(28), dpToPx(28))
            setOnClickListener {
                dismiss(this@CallerIdOverlayService)
            }
        }

        topRow.addView(categoryBadge)
        topRow.addView(spacer)
        topRow.addView(dismissButton)
        card.addView(topRow)

        // Caller name
        val nameText = TextView(this).apply {
            text = callerName
            setTextColor(0xFFF5F5F5.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dpToPx(8), 0, dpToPx(2))
        }
        card.addView(nameText)

        // Phone number
        val phoneText = TextView(this).apply {
            text = phoneNumber
            setTextColor(0xFFB0B0B0.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setPadding(0, 0, 0, dpToPx(8))
        }
        card.addView(phoneText)

        // Bottom row: spam score badge (only if we have a score)
        if (spamScore >= 0) {
            val scoreRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dpToPx(4), 0, 0)
            }

            val scoreLabel = TextView(this).apply {
                text = "Spam Score: "
                setTextColor(0xFFB0B0B0.toInt())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            }

            val scoreValue = TextView(this).apply {
                text = "$spamScore/100"
                setTextColor(categoryColor)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                typeface = Typeface.DEFAULT_BOLD
                background = scoreBadgeBackground
                setPadding(dpToPx(8), dpToPx(3), dpToPx(8), dpToPx(3))
            }

            scoreRow.addView(scoreLabel)
            scoreRow.addView(scoreValue)
            card.addView(scoreRow)
        }

        // TruCaller branding
        val brandText = TextView(this).apply {
            text = "TruCaller ID"
            setTextColor(0xFFFFCD00.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.END
            setPadding(0, dpToPx(6), 0, 0)
        }
        card.addView(brandText)

        return card
    }

    private fun removeOverlay() {
        try {
            if (overlayView != null) {
                windowManager?.removeView(overlayView)
                overlayView = null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error removing overlay", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        removeOverlay()
    }

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            resources.displayMetrics
        ).toInt()
    }
}
