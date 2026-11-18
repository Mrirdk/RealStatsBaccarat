package com.stats.baccaratoverlay

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat

class FloatingOverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: View
    private lateinit var params: WindowManager.LayoutParams

    private val history = mutableListOf<String>()
    private var isMinimized = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startAndroid14Foreground()
        setupFloatingWindow()
    }

    private fun startAndroid14Foreground() {
        val channelId = "baccarat_real_stats_channel"
        val channel = NotificationChannel(
            channelId,
            "Bảng Thống Kê Thực Tế",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Real-Stats Baccarat đang chạy")
            .setContentText("Cửa sổ nổi đang hiển thị trên màn hình")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                1001,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(1001, notification)
        }
    }

    @SuppressLint("ClickableViewAccessibility", "InflateParams")
    private fun setupFloatingWindow() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_layout, null)

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 40
        params.y = 180

        windowManager.addView(overlayView, params)

        val dragHeader = overlayView.findViewById<LinearLayout>(R.id.dragHeader)
        val contentContainer = overlayView.findViewById<LinearLayout>(R.id.contentContainer)
        val btnMinimize = overlayView.findViewById<TextView>(R.id.btnMinimize)
        val btnClose = overlayView.findViewById<TextView>(R.id.btnClose)

        val btnPlayer = overlayView.findViewById<Button>(R.id.btnPlayer)
        val btnBanker = overlayView.findViewById<Button>(R.id.btnBanker)
        val btnTie = overlayView.findViewById<Button>(R.id.btnTie)
        val btnUndo = overlayView.findViewById<Button>(R.id.btnUndo)
        val btnReset = overlayView.findViewById<Button>(R.id.btnReset)

        // Kéo thả di chuyển cửa sổ nổi trên màn hình
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        dragHeader.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(overlayView, params)
                    true
                }
                else -> false
            }
        }

        // Thu gọn / Mở rộng cửa sổ nổi
        btnMinimize.setOnClickListener {
            isMinimized = !isMinimized
            if (isMinimized) {
                contentContainer.visibility = View.GONE
                btnMinimize.text = "+"
            } else {
                contentContainer.visibility = View.VISIBLE
                btnMinimize.text = "—"
            }
        }

        // Tắt hoàn toàn công cụ
        btnClose.setOnClickListener {
            stopSelf()
        }

        // Nhập dữ liệu thực tế từng ván
        btnPlayer.setOnClickListener {
            history.add("P")
            refreshUI()
        }
        btnBanker.setOnClickListener {
            history.add("B")
            refreshUI()
        }
        btnTie.setOnClickListener {
            history.add("T")
            refreshUI()
        }
        btnUndo.setOnClickListener {
            if (history.isNotEmpty()) {
                history.removeAt(history.lastIndex)
                refreshUI()
            }
        }
        btnReset.setOnClickListener {
            history.clear()
            refreshUI()
        }

        refreshUI()
    }

    private fun refreshUI() {
        val res = BaccaratMathEngine.analyze(history)

        val tvSignal = overlayView.findViewById<TextView>(R.id.tvSignal)
        val tvDistribution = overlayView.findViewById<TextView>(R.id.tvDistribution)
        val tvMarkov = overlayView.findViewById<TextView>(R.id.tvMarkov)
        val tvStreak = overlayView.findViewById<TextView>(R.id.tvStreak)
        val tvZScore = overlayView.findViewById<TextView>(R.id.tvZScore)

        tvSignal.text = res.signalText
        when {
            res.signalText.contains("PLAYER") -> tvSignal.setTextColor(Color.parseColor("#4FC3F7"))
            res.signalText.contains("BANKER") -> tvSignal.setTextColor(Color.parseColor("#FF5252"))
            else -> tvSignal.setTextColor(Color.parseColor("#FFD740"))
        }

        tvDistribution.text = res.distributionText
        tvMarkov.text = res.markovText
        tvStreak.text = res.streakText
        tvZScore.text = res.zScoreText
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::overlayView.isInitialized) {
            windowManager.removeView(overlayView)
        }
    }
}
