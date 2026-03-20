package com.byron.trucaller.service

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.util.TypedValue
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.byron.trucaller.TruCallerApplication
import com.byron.trucaller.data.model.CallerIdEntry
import com.byron.trucaller.data.model.SpamCategory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CallerIdOverlayService : Service() {

    companion object {
        private const val TAG = "CallerIdOverlay"
        private const val EXTRA_PHONE_NUMBER = "extra_phone_number"

        // Spam category colors
        private const val COLOR_SAFE = 0xFF2E7D32.toInt()           // Green
        private const val COLOR_SUSPECTED_SPAM = 0xFFFFCD00.toInt() // Yellow
        private const val COLOR_SPAM = 0xFFFF9800.toInt()           // Orange
        private const val COLOR_FRAUD = 0xFFD90000.toInt()          // Red

        // Brand color
        private const val COLOR_BRAND = 0xFFFFCD00.toInt()

        // Dark theme colors
        private const val DARK_CARD_BG = 0xE61E1E1E.toInt()        // Semi-transparent dark
        private const val DARK_CARD_BG_SOLID = 0xFF1E1E1E.toInt()
        private const val DARK_TEXT_PRIMARY = 0xFFF5F5F5.toInt()
        private const val DARK_TEXT_SECONDARY = 0xFFB0B0B0.toInt()
        private const val DARK_SURFACE_ELEVATED = 0xFF252525.toInt()
        private const val DARK_DIVIDER = 0xFF333333.toInt()

        // Light theme colors
        private const val LIGHT_CARD_BG = 0xE6FFFFFF.toInt()        // Semi-transparent white
        private const val LIGHT_CARD_BG_SOLID = 0xFFFFFFFF.toInt()
        private const val LIGHT_TEXT_PRIMARY = 0xFF1A1A1A.toInt()
        private const val LIGHT_TEXT_SECONDARY = 0xFF616161.toInt()
        private const val LIGHT_SURFACE_ELEVATED = 0xFFF5F5F5.toInt()
        private const val LIGHT_DIVIDER = 0xFFE0E0E0.toInt()

        // Animation durations
        private const val ANIM_ENTRANCE_DURATION = 350L
        private const val ANIM_EXIT_DURATION = 250L
        private const val SWIPE_VELOCITY_THRESHOLD = 300f

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
    private var isDismissing = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val phoneNumber = intent?.getStringExtra(EXTRA_PHONE_NUMBER)
        if (phoneNumber.isNullOrBlank()) {
            stopSelf()
            return START_NOT_STICKY
        }

        // Remove any existing overlay before showing a new one
        removeOverlay()
        isDismissing = false

        // Look up the number
        val app = applicationContext as? TruCallerApplication
        if (app == null) {
            Log.e(TAG, "Failed to get TruCallerApplication")
            stopSelf()
            return START_NOT_STICKY
        }

        val callerIdRepo = app.container.callerIdRepository

        // Check the cache first — the call screening service may have already
        // looked up this number, so we can avoid a redundant query.
        val cachedResult = CallerIdCache.get(phoneNumber)
        if (cachedResult != null) {
            Log.d(TAG, "Using cached caller ID result for $phoneNumber")
            showOrNotify(phoneNumber, cachedResult.callerIdEntry)
        } else {
            // Perform lookup asynchronously to avoid blocking the main thread (ANR)
            CoroutineScope(Dispatchers.IO).launch {
                val lookupResult = try {
                    callerIdRepo.lookupNumber(phoneNumber)
                } catch (e: Exception) {
                    Log.e(TAG, "Error looking up number: $phoneNumber", e)
                    null
                }
                withContext(Dispatchers.Main) {
                    showOrNotify(phoneNumber, lookupResult?.callerIdEntry)
                }
            }
        }

        return START_NOT_STICKY
    }

    private fun showOrNotify(phoneNumber: String, entry: CallerIdEntry?) {
        // If overlay permission is not granted, fall back to a notification
        if (!Settings.canDrawOverlays(this)) {
            Log.d(TAG, "Overlay permission denied — showing notification instead")
            CallNotificationHelper.showCallerIdNotification(
                context = this,
                callerName = entry?.name ?: "Unknown Caller",
                number = phoneNumber,
                spamScore = entry?.spamScore ?: -1,
                category = entry?.category ?: SpamCategory.SAFE
            )
            stopSelf()
            return
        }
        showOverlay(phoneNumber, entry)
    }

    private fun isDarkMode(): Boolean {
        val uiMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return uiMode == Configuration.UI_MODE_NIGHT_YES
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showOverlay(phoneNumber: String, entry: CallerIdEntry?) {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
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
            y = dpToPx(48)
            // Enable blur-behind for frosted glass effect on API 31+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                flags = flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
                blurBehindRadius = 20
            }
        }

        val callerName = entry?.name ?: "Unknown Caller"
        val spamScore = entry?.spamScore ?: -1
        val category = entry?.category ?: SpamCategory.SAFE

        // Build the overlay wrapped in a margin container
        val wrapper = FrameLayout(this).apply {
            setPadding(dpToPx(16), 0, dpToPx(16), 0)
        }
        val cardView = buildOverlayView(callerName, phoneNumber, spamScore, category)
        wrapper.addView(
            cardView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        )

        overlayView = wrapper

        // Set up swipe-to-dismiss gesture detector
        val gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                // Detect upward fling for dismiss
                if (velocityY < -SWIPE_VELOCITY_THRESHOLD) {
                    animateExit()
                    return true
                }
                return false
            }
        })

        // Touch handling: support both drag and swipe-to-dismiss
        var initialY = 0
        var initialTouchY = 0f
        var isDragging = false

        wrapper.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialY = layoutParams.y
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaY = (event.rawY - initialTouchY).toInt()
                    if (Math.abs(deltaY) > dpToPx(4)) {
                        isDragging = true
                    }
                    if (isDragging) {
                        layoutParams.y = initialY + deltaY
                        try {
                            windowManager?.updateViewLayout(overlayView, layoutParams)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error updating overlay position", e)
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        // Tap -- no action needed (dismiss button handles taps)
                    }
                    true
                }
                else -> false
            }
        }

        try {
            windowManager?.addView(overlayView, layoutParams)
            // Animate entrance
            animateEntrance(wrapper)
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
        val dark = isDarkMode()

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

        val cardBg = if (dark) DARK_CARD_BG else LIGHT_CARD_BG
        val cardBgSolid = if (dark) DARK_CARD_BG_SOLID else LIGHT_CARD_BG_SOLID
        val textPrimary = if (dark) DARK_TEXT_PRIMARY else LIGHT_TEXT_PRIMARY
        val textSecondary = if (dark) DARK_TEXT_SECONDARY else LIGHT_TEXT_SECONDARY
        val surfaceElevated = if (dark) DARK_SURFACE_ELEVATED else LIGHT_SURFACE_ELEVATED
        val dividerColor = if (dark) DARK_DIVIDER else LIGHT_DIVIDER

        // ── Card background ────────────────────────────────────────────────
        val cardBackground = GradientDrawable().apply {
            setColor(cardBgSolid)
            cornerRadius = dpToPx(16).toFloat()
        }

        // ── Root: horizontal FrameLayout with color strip + content ────────
        val rootCard = FrameLayout(this).apply {
            background = cardBackground
            elevation = dpToPx(12).toFloat()
            clipToOutline = true
            outlineProvider = object : android.view.ViewOutlineProvider() {
                override fun getOutline(view: View, outline: android.graphics.Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, dpToPx(16).toFloat())
                }
            }
        }

        // Frosted glass effect: on API 31+, use a semi-transparent background
        // so the system's window blur creates a glass-like appearance.
        // The actual blur is applied to the window via FLAG_BLUR_BEHIND.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            cardBackground.setColor(cardBg) // Semi-transparent for glass effect
        }

        // ── Left color strip ───────────────────────────────────────────────
        val colorStrip = View(this).apply {
            val stripBg = GradientDrawable().apply {
                setColor(categoryColor)
                cornerRadii = floatArrayOf(
                    dpToPx(16).toFloat(), dpToPx(16).toFloat(), // top-left
                    0f, 0f,                                       // top-right
                    0f, 0f,                                       // bottom-right
                    dpToPx(16).toFloat(), dpToPx(16).toFloat()  // bottom-left
                )
            }
            background = stripBg
        }
        rootCard.addView(
            colorStrip,
            FrameLayout.LayoutParams(dpToPx(5), FrameLayout.LayoutParams.MATCH_PARENT).apply {
                gravity = Gravity.START
            }
        )

        // ── Content area (to the right of the strip) ───────────────────────
        val contentLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(5) + dpToPx(16), dpToPx(14), dpToPx(14), dpToPx(12))
        }
        rootCard.addView(
            contentLayout,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        )

        // ── Top row: avatar + name/number + dismiss button ─────────────────
        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        // Circular avatar with initials
        val initials = extractInitials(callerName)
        val avatarSize = dpToPx(44)
        val avatarView = object : View(this@CallerIdOverlayService) {
            private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = categoryColor
                style = Paint.Style.FILL
            }
            private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = spToPx(16).toFloat()
                typeface = Typeface.DEFAULT_BOLD
                textAlign = Paint.Align.CENTER
            }

            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                val cx = width / 2f
                val cy = height / 2f
                val radius = Math.min(cx, cy)
                canvas.drawCircle(cx, cy, radius, bgPaint)

                val textY = cy - (textPaint.descent() + textPaint.ascent()) / 2f
                canvas.drawText(initials, cx, textY, textPaint)
            }
        }
        avatarView.layoutParams = LinearLayout.LayoutParams(avatarSize, avatarSize).apply {
            marginEnd = dpToPx(12)
        }
        topRow.addView(avatarView)

        // Name and number column
        val nameNumberColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val nameText = TextView(this).apply {
            text = callerName
            setTextColor(textPrimary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        nameNumberColumn.addView(nameText)

        val phoneText = TextView(this).apply {
            text = phoneNumber
            setTextColor(textSecondary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            maxLines = 1
            setPadding(0, dpToPx(2), 0, 0)
        }
        nameNumberColumn.addView(phoneText)

        topRow.addView(nameNumberColumn)

        // Dismiss X button
        val dismissBg = GradientDrawable().apply {
            setColor(surfaceElevated)
            cornerRadius = dpToPx(14).toFloat()
        }
        val dismissButton = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            setColorFilter(textSecondary)
            background = dismissBg
            setPadding(dpToPx(6), dpToPx(6), dpToPx(6), dpToPx(6))
            layoutParams = LinearLayout.LayoutParams(dpToPx(28), dpToPx(28)).apply {
                marginStart = dpToPx(8)
            }
            setOnClickListener { animateExit() }
        }
        topRow.addView(dismissButton)

        contentLayout.addView(topRow)

        // ── Divider line ───────────────────────────────────────────────────
        val divider = View(this).apply {
            setBackgroundColor(dividerColor)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(1)
            ).apply {
                topMargin = dpToPx(10)
                bottomMargin = dpToPx(8)
            }
        }
        contentLayout.addView(divider)

        // ── Bottom row: category badge + spam score + branding ─────────────
        val bottomRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        // Category badge
        val badgeBackground = GradientDrawable().apply {
            setColor(categoryColor)
            cornerRadius = dpToPx(10).toFloat()
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
        bottomRow.addView(categoryBadge)

        // Spam score (if available)
        if (spamScore >= 0) {
            val scoreBadgeBg = GradientDrawable().apply {
                setColor(surfaceElevated)
                cornerRadius = dpToPx(8).toFloat()
                setStroke(dpToPx(1), categoryColor)
            }
            val scoreText = TextView(this).apply {
                text = "Score: $spamScore/100"
                setTextColor(categoryColor)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                typeface = Typeface.DEFAULT_BOLD
                background = scoreBadgeBg
                setPadding(dpToPx(8), dpToPx(3), dpToPx(8), dpToPx(3))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginStart = dpToPx(8)
                }
            }
            bottomRow.addView(scoreText)
        }

        // Spacer
        val spacer = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, 0, 1f)
        }
        bottomRow.addView(spacer)

        // TruCaller branding with shield icon
        val brandRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val shieldIcon = object : View(this@CallerIdOverlayService) {
            private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = COLOR_BRAND
                style = Paint.Style.FILL
            }

            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                val w = width.toFloat()
                val h = height.toFloat()
                val path = android.graphics.Path().apply {
                    // Shield shape
                    moveTo(w * 0.5f, h * 0.05f)
                    lineTo(w * 0.9f, h * 0.2f)
                    lineTo(w * 0.9f, h * 0.55f)
                    quadTo(w * 0.85f, h * 0.8f, w * 0.5f, h * 0.95f)
                    quadTo(w * 0.15f, h * 0.8f, w * 0.1f, h * 0.55f)
                    lineTo(w * 0.1f, h * 0.2f)
                    close()
                }
                canvas.drawPath(path, paint)

                // Checkmark inside
                val checkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0xFF1A1A1A.toInt()
                    style = Paint.Style.STROKE
                    strokeWidth = w * 0.12f
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                }
                val checkPath = android.graphics.Path().apply {
                    moveTo(w * 0.3f, h * 0.5f)
                    lineTo(w * 0.45f, h * 0.65f)
                    lineTo(w * 0.7f, h * 0.35f)
                }
                canvas.drawPath(checkPath, checkPaint)
            }
        }
        shieldIcon.layoutParams = LinearLayout.LayoutParams(dpToPx(14), dpToPx(14)).apply {
            marginEnd = dpToPx(4)
        }
        brandRow.addView(shieldIcon)

        val brandText = TextView(this).apply {
            text = "TruCaller"
            setTextColor(COLOR_BRAND)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.DEFAULT_BOLD
        }
        brandRow.addView(brandText)

        bottomRow.addView(brandRow)
        contentLayout.addView(bottomRow)

        return rootCard
    }

    // ── Animations ─────────────────────────────────────────────────────────

    private fun animateEntrance(view: View) {
        // Start off-screen above and transparent
        view.translationY = -dpToPx(80).toFloat()
        view.alpha = 0f

        val slideDown = ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, -dpToPx(80).toFloat(), 0f).apply {
            duration = ANIM_ENTRANCE_DURATION
            interpolator = DecelerateInterpolator(1.5f)
        }
        val fadeIn = ObjectAnimator.ofFloat(view, View.ALPHA, 0f, 1f).apply {
            duration = ANIM_ENTRANCE_DURATION
            interpolator = DecelerateInterpolator()
        }

        AnimatorSet().apply {
            playTogether(slideDown, fadeIn)
            start()
        }
    }

    private fun animateExit() {
        if (isDismissing) return
        isDismissing = true

        val view = overlayView ?: run {
            stopSelf()
            return
        }

        val slideUp = ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, view.translationY, -dpToPx(120).toFloat()).apply {
            duration = ANIM_EXIT_DURATION
            interpolator = AccelerateInterpolator(1.5f)
        }
        val fadeOut = ObjectAnimator.ofFloat(view, View.ALPHA, view.alpha, 0f).apply {
            duration = ANIM_EXIT_DURATION
            interpolator = AccelerateInterpolator()
        }

        AnimatorSet().apply {
            playTogether(slideUp, fadeOut)
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    dismiss(this@CallerIdOverlayService)
                }
            })
            start()
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private fun extractInitials(name: String): String {
        val parts = name.trim().split("\\s+".toRegex())
        return when {
            parts.size >= 2 -> "${parts[0].first().uppercaseChar()}${parts[1].first().uppercaseChar()}"
            parts.isNotEmpty() && parts[0].isNotEmpty() -> "${parts[0].first().uppercaseChar()}"
            else -> "?"
        }
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

    private fun spToPx(sp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            sp.toFloat(),
            resources.displayMetrics
        ).toInt()
    }
}
