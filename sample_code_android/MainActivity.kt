import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.autel.enterprise.R
import org.json.JSONObject

class MainActivity : AppCompatActivity() {
    private val mqttHelper = MqttHelper()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 初始化并连接
        mqttHelper.init()
        mqttHelper.connectWithRetry()

        binding.sendButton.setOnClickListener {
            val message = JSONObject().apply {
                put("method", "get_info")
                put("data", JSONObject().put("device_id", "123"))
            }.toString()

            mqttHelper.publish("device/command", message)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mqttHelper.unsubscribeAll()
        mqttHelper.close()
    }
} 