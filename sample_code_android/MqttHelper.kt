import kotlinx.coroutines.*
import org.eclipse.paho.client.mqttv3.*
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import java.util.concurrent.TimeUnit
import kotlin.math.pow
import org.json.JSONObject
import android.util.Log

class MqttHelper {
    private val BROKER_URL = "tcp://127.0.0.1:1883"
    private val CLIENT_ID = "AndroidClient"
    private var mqttClient: MqttAsyncClient? = null
    private var isConnected = false
    private var retryJob: Job? = null
    private var savedGatewaySn: String? = null
    private var savedDroneSn: String? = null
    private val DEVICE_INFO_TOPIC = "device/info"
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // 初始化 MQTT 客户端
    fun init() {
        try {
            val persistence = MemoryPersistence()
            mqttClient = MqttAsyncClient(BROKER_URL, CLIENT_ID, persistence)

            mqttClient?.setCallback(object : MqttCallback {
                override fun connectionLost(cause: Throwable?) {
                    isConnected = false
                    Log.d("MQTT", "Connection lost: ${cause?.message}")
                }

                override fun messageArrived(topic: String?, message: MqttMessage?) {
                    try {
                        Log.d("MQTT", "Message received: $topic -> ${message.toString()}")
                        
                        val messageStr = message?.toString() ?: return
                        
                        when {
                            topic == DEVICE_INFO_TOPIC -> {
                                val jsonObject = JSONObject(messageStr)
                                when (jsonObject.optString("method")) {
                                    "gateway_info" -> {
                                        try {
                                            val data = jsonObject.getJSONObject("data")
                                            val gatewaySn = data.optString("gateway_sn")
                                            if (!gatewaySn.isNullOrBlank()) {
                                                savedGatewaySn = gatewaySn
                                                Log.d("MQTT", "Received gateway info - Gateway: $savedGatewaySn")
                                                // 订阅网关相关主题
                                                subscribeToGatewayTopics()
                                            }

                                            val droneSn = data.optString("drone_sn")
                                            if (!droneSn.isNullOrBlank() && savedDroneSn == null) {
                                                savedDroneSn = droneSn
                                                Log.d("MQTT", "Received drone info - Drone: $savedDroneSn")
                                                subscribeToDroneTopics()
                                            }
                                        } catch (e: Exception) {
                                            Log.e("MQTT", "Error processing gateway info: ${e.message}")
                                        }
                                    }
                                    "drone_info" -> {
                                        try {
                                            if (savedDroneSn == null) {
                                                val data = jsonObject.getJSONObject("data")
                                                val droneSn = data.optString("drone_sn")
                                                if (!droneSn.isNullOrBlank()) {
                                                    savedDroneSn = droneSn
                                                    Log.d("MQTT", "Received drone info - Drone: $savedDroneSn")
                                                    subscribeToDroneTopics()
                                                }
                                            }
                                        } catch (e: Exception) {
                                            Log.e("MQTT", "Error processing drone info: ${e.message}")
                                        }
                                    }
                                }
                            }
                            topic?.contains(savedGatewaySn ?: "") == true -> {
                                Log.d("MQTT", "Received gateway message on topic $topic: $messageStr")
                            }
                            topic?.contains(savedDroneSn ?: "") == true -> {
                                Log.d("MQTT", "Received drone message on topic $topic: $messageStr")
                            }
                            else -> {
                                Log.d("MQTT", "Received other message on topic $topic: $messageStr")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("MQTT", "Error processing message: ${e.message}")
                        e.printStackTrace()
                    }
                }

                override fun deliveryComplete(token: IMqttDeliveryToken?) {
                    Log.d("MQTT", "Message delivered: ${token?.message}")
                }
            })
            Log.d("MQTT", "MQTT client initialized successfully")
        } catch (e: MqttException) {
            e.printStackTrace()
            throw RuntimeException("Failed to initialize MQTT client", e)
        }
    }

    // 尝试连接 Broker
    fun connectWithRetry() {
        val options = MqttConnectOptions().apply {
            isAutomaticReconnect = true
            isCleanSession = true
            connectionTimeout = 10
        }

        retryJob = coroutineScope.launch {
            var retryCount = 0
            while (!isConnected) {
                try {
                    withContext(Dispatchers.IO) {
                        mqttClient?.connect(options)?.waitForCompletion(10000)
                    }
                    withContext(Dispatchers.Main) {
                        onConnected()
                    }
                } catch (e: MqttException) {
                    retryCount++
                    val backoffTime = TimeUnit.SECONDS.toMillis((2.0.pow(retryCount)).toLong().coerceAtMost(60))
                    Log.d("MQTT", "Connection failed: ${e.message}. Retrying in ${backoffTime / 1000}s...")
                    delay(backoffTime)
                }
            }
        }
    }

    // 停止连接
    fun disconnect() {
        try {
            if (isConnected) {
                mqttClient?.disconnect()?.waitForCompletion()
                isConnected = false
                Log.d("MQTT", "Disconnected from MQTT broker")
            }
        } catch (e: MqttException) {
            Log.e("MQTT", "Error while disconnecting: ${e.message}")
        }
    }

    // 重新连接
    fun reconnect() {
        if (!isConnected) {
            Log.d("MQTT", "Reconnecting to MQTT broker...")
            connectWithRetry()
        }
    }

    // 关闭客户端
    fun close() {
        try {
            coroutineScope.cancel() // 取消所有协程
            if (mqttClient?.isConnected == true) {
                mqttClient?.disconnect()?.waitForCompletion()
            }
            mqttClient?.close()
            isConnected = false
            Log.d("MQTT", "MQTT client closed")
        } catch (e: MqttException) {
            Log.e("MQTT", "Error while closing MQTT client: ${e.message}")
        }
    }

    // 发布消息
    fun publish(topic: String, message: String) {
        if (isConnected) {
            val mqttMessage = MqttMessage(message.toByteArray()).apply { qos = 1 }
            mqttClient?.publish(topic, mqttMessage)
            Log.d("MQTT", "Published: $topic -> $message")
        } else {
            Log.d("MQTT", "Cannot publish. MQTT is not connected")
        }
    }

    // 订阅主题
    fun subscribe(topic: String) {
        if (!isConnected) {
            Log.d("MQTT", "Cannot subscribe to $topic - not connected")
            return
        }
        
        try {
            mqttClient?.subscribe(topic, 1, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    Log.d("MQTT", "Subscribed to topic: $topic")
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    Log.e("MQTT", "Failed to subscribe to $topic: ${exception?.message}")
                    if (exception is MqttException && 
                        exception.reasonCode == MqttException.REASON_CODE_CLIENT_NOT_CONNECTED.toInt()) {
                        isConnected = false
                        reconnect()
                    }
                }
            })
        } catch (e: MqttException) {
            Log.e("MQTT", "Error subscribing to $topic: ${e.message}")
            if (e.reasonCode == MqttException.REASON_CODE_CLIENT_NOT_CONNECTED.toInt()) {
                isConnected = false
                reconnect()
            }
        }
    }

    private fun subscribeToGatewayTopics() {
        if (savedGatewaySn.isNullOrBlank()) {
            Log.d("MQTT", "Cannot subscribe to gateway topics - no gateway SN")
            return
        }

        val topics = listOf(
            "thing/product/$savedGatewaySn/osd",
            "thing/product/$savedGatewaySn/state",
            "thing/product/$savedGatewaySn/services_reply",
            "thing/product/$savedGatewaySn/events",
            "thing/product/$savedGatewaySn/requests",
            "sys/product/$savedGatewaySn/status",
            "thing/product/$savedGatewaySn/property/set_reply",
            "thing/product/$savedGatewaySn/get_token"
        )

        topics.forEach { topic ->
            subscribe(topic)
        }
    }

    private fun subscribeToDroneTopics() {
        if (!isConnected || savedDroneSn.isNullOrBlank()) {
            Log.d("MQTT", "Cannot subscribe to drone topics - not connected or no drone SN")
            return
        }

        val topics = listOf(
            "thing/product/$savedDroneSn/osd",
            "thing/product/$savedDroneSn/state"
        )

        topics.forEach { topic ->
            subscribe(topic)
        }
    }

    private fun onConnected() {
        isConnected = true
        subscribe(DEVICE_INFO_TOPIC)
        Log.d("MQTT", "Connected and subscribed to $DEVICE_INFO_TOPIC")
    }

    // 取消订阅主题
    fun unsubscribe(topic: String) {
        if (!isConnected) {
            Log.d("MQTT", "Cannot unsubscribe from $topic - not connected")
            return
        }
        
        try {
            mqttClient?.unsubscribe(topic, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    Log.d("MQTT", "Unsubscribed from topic: $topic")
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    Log.e("MQTT", "Failed to unsubscribe from $topic: ${exception?.message}")
                }
            })
        } catch (e: MqttException) {
            Log.e("MQTT", "Error unsubscribing from $topic: ${e.message}")
        }
    }

    // 取消订阅所有主题
    fun unsubscribeAll() {
        if (!isConnected) {
            Log.d("MQTT", "Cannot unsubscribe - not connected")
            return
        }

        try {
            // 取消订阅设备信息主题
            unsubscribe(DEVICE_INFO_TOPIC)

            // 取消订阅网关主题
            savedGatewaySn?.let { gatewaySn ->
                val gatewayTopics = listOf(
                    "thing/product/$gatewaySn/osd",
                    "thing/product/$gatewaySn/state",
                    "thing/product/$gatewaySn/services_reply",
                    "thing/product/$gatewaySn/events",
                    "thing/product/$gatewaySn/requests",
                    "sys/product/$gatewaySn/status",
                    "thing/product/$gatewaySn/property/set_reply",
                    "thing/product/$gatewaySn/get_token"
                )
                gatewayTopics.forEach { unsubscribe(it) }
            }

            // 取消订阅无人机主题
            savedDroneSn?.let { droneSn ->
                val droneTopics = listOf(
                    "thing/product/$droneSn/osd",
                    "thing/product/$droneSn/state"
                )
                droneTopics.forEach { unsubscribe(it) }
            }

            // 重置保存的 SN
            savedGatewaySn = null
            savedDroneSn = null

            Log.d("MQTT", "Unsubscribed from all topics")
        } catch (e: Exception) {
            Log.e("MQTT", "Error unsubscribing from all topics: ${e.message}")
        }
    }
} 