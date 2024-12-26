---

### 中文版  

```markdown
# Autel 上云集成应用  

一个参考性的 Android 应用，帮助客户通过 MQTT 使用上云 API 与 Autel Enterprise 设备进行通信。功能包括：  

- 自动连接 Autel Enterprise MQTT Broker  
- JSON 消息处理  
- 主题订阅  
- 生命周期管理  

## 核心功能  

1. **自动连接管理**  
   - 使用指数退避算法实现自动重连。  

2. **消息处理**  
   - 使用 QoS 1 确保消息可靠传递。  
   - 支持 JSON 格式的数据交换解析。  

3. **主题订阅**  
   - 自动订阅与设备、网关、无人机相关的核心主题。  

4. **生命周期管理**  
   - 在应用销毁时正确清理 MQTT 连接和订阅。  

## 快速开始  

1. **添加依赖**  
   在 `build.gradle` 中添加以下依赖：  
   ```gradle
   dependencies {
       implementation 'org.eclipse.paho:org.eclipse.paho.client.mqttv3:1.2.5'
       implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.6.4'
   }
```