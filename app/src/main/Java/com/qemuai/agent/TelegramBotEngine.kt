package com.qemuai.agent

import android.content.Context
import android.hardware.camera2.CameraManager
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class TelegramBotEngine(private val context: Context) {

    private val botToken = "7517702293:AAHHoUkK9Mh0bt3fTco3oZ1DWwwwaSfIefg"
    private val defaultChatId = "7922892230"
    
    @Volatile
    private var isListening = false
    private var lastUpdateId = 0

    /**
     * Telegram arka plan dinleyicisini (Long Polling) başlatır.
     */
    fun startListening() {
        if (isListening) return
        isListening = true

        Thread {
            while (isListening) {
                try {
                    val urlString = "https://api.telegram.org/bot$botToken/getUpdates?offset=${lastUpdateId + 1}&timeout=30"
                    val url = URL(urlString)
                    val conn = url.openConnection() as HttpURLConnection
                    conn.requestMethod = "GET"
                    conn.connectTimeout = 35000
                    conn.readTimeout = 35000

                    if (conn.responseCode == 200) {
                        val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                        parseAndProcessUpdates(responseText)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    // Bağlantı kopması veya ağ hatalarında tekrar denemeden önce kısa bir süre bekle
                    Thread.sleep(5000)
                }
            }
        }.start()
    }

    /**
     * Dinleyiciyi durdurur.
     */
    fun stopListening() {
        isListening = false
    }

    /**
     * Telegram'dan gelen JSON verisini ayrıştırır ve komutları işler.
     */
    private fun parseAndProcessUpdates(jsonResponse: String) {
        try {
            val jsonObject = JSONObject(jsonResponse)
            if (!jsonObject.optBoolean("ok")) return

            val resultArray = jsonObject.optJSONArray("result") ?: return

            for (i in 0 until resultArray.length()) {
                val update = resultArray.getJSONObject(i)
                val updateId = update.getInt("update_id")
                
                // Offset güncellemesi (Aynı mesajın tekrar işlenmesini önler)
                if (updateId > lastUpdateId) {
                    lastUpdateId = updateId
                }

                if (update.has("message")) {
                    val message = update.getJSONObject("message")
                    val chatId = message.getJSONObject("chat").getLong("id").toString()
                    val text = message.optString("text", "")

                    if (text.isNotEmpty()) {
                        handleIncomingCommand(text.trim(), chatId)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Gelen komuta göre eylemleri tetikler.
     */
    fun handleIncomingCommand(command: String, chatId: String = defaultChatId) {
        when {
            command.startsWith("/help") -> {
                sendMessage(chatId, "QemuAI Komut Listesi:\n/screenshot\n/flashlight on|off\n/help")
            }
            command.startsWith("/flashlight on") -> {
                toggleFlashlight(true)
                sendMessage(chatId, "Fener AÇILDI.")
            }
            command.startsWith("/flashlight off") -> {
                toggleFlashlight(false)
                sendMessage(chatId, "Fener KAPATILDI.")
            }
            command.startsWith("/screenshot") -> {
                QemuAccessibilityService.instance?.takeScreenCapture { bitmap ->
                    if (bitmap != null) {
                        sendMessage(chatId, "Ekran görüntüsü başarıyla alındı.")
                    } else {
                        sendMessage(chatId, "Ekran görüntüsü alınamadı (Erişilebilirlik izni gerek).")
                    }
                }
            }
        }
    }

    private fun toggleFlashlight(status: Boolean) {
        try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = cameraManager.cameraIdList[0]
            cameraManager.setTorchMode(cameraId, status)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun sendMessage(chatId: String, text: String) {
        Thread {
            try {
                val url = URL("https://api.telegram.org/bot$botToken/sendMessage")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true

                val jsonPayload = JSONObject().apply {
                    put("chat_id", chatId)
                    put("text", text)
                }.toString()

                val os = conn.outputStream
                val writer = OutputStreamWriter(os, "UTF-8")
                writer.write(jsonPayload)
                writer.flush()
                writer.close()
                conn.inputStream.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }
}
