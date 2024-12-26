# Autel Cloud Integration App  

A reference Android application for customers to communicate with Autel Enterprise devices via cloud APIs using MQTT. The app includes features like:  

- Automatic connection to the Autel Enterprise MQTT broker  
- JSON message handling  
- Topic subscription  
- Full lifecycle management  

## Key Features  

1. **Automatic Connection Management**  
   - Auto-reconnect with exponential backoff.  

2. **Message Handling**  
   - Publish and receive messages with QoS 1 for reliable delivery.  
   - JSON parsing for structured data exchange.  

3. **Topic Subscription**  
   - Automatic subscription to key topics related to devices, gateways, and drones.  

4. **Lifecycle Management**  
   - Proper cleanup of MQTT connections and subscriptions during app destruction.  

## Getting Started  

1. **Add Dependencies**  
   Add the following dependencies in your `build.gradle`:  
   ```gradle
   dependencies {
       implementation 'org.eclipse.paho:org.eclipse.paho.client.mqttv3:1.2.5'
       implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.6.4'
   }
   ```