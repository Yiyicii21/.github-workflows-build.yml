
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.layout_floating_icon)

        // Telegram Bot motorunu arka planda başlat
        val botEngine = TelegramBotEngine(applicationContext)
        botEngine.startListening()
    }
}
