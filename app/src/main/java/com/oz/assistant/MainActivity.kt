package com.oz.assistant

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import com.oz.assistant.overlay.OverlayService

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Проверяем разрешение на overlay
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
            finish()
            return
        }

        // 2. Запускаем OverlayService
        startService(Intent(this, OverlayService::class.java))

        // 3. Закрываем Activity навсегда
        finish()
    }
}
