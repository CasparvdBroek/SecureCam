# SecureCam - WebRTC Camera Server

This Android application implements a WebRTC camera server that streams your device camera to web browsers over the local network. The app runs as a foreground service with a persistent notification and provides a simple web interface for viewing the camera stream.

## Features

- **WebRTC Camera Server**: Streams device camera to web browsers
- **Foreground Service**: Runs continuously in the background with notification
- **Local Network Access**: Accessible from any device on the same network
- **Real-time Video**: Low-latency video streaming using WebRTC
- **Permission Management**: Handles camera and microphone permissions
- **Service Control UI**: Start/Stop buttons with real-time status

## Implementation Details

### Components

1. **ForegroundService.java**: Main service that manages WebRTC streaming
   - Initializes WebRTC manager and signaling server
   - Handles service lifecycle and cleanup
   - Shows notification with server URL

2. **WebRTCManager.java**: Core WebRTC functionality
   - Manages peer connections and media streams
   - Handles camera capture and video encoding
   - Implements WebRTC signaling and ICE candidate handling

3. **WebRTCSignalingServer.java**: HTTP server for WebRTC signaling
   - Provides REST endpoints for offer/answer exchange
   - Handles ICE candidate signaling
   - Serves the web client interface

4. **MainActivity.java**: User interface for service control
   - Permission request handling
   - Service start/stop controls
   - Displays server URL and connection status

5. **ServiceStatusChecker.java**: Utility for checking service status
   - Uses ActivityManager to verify service is running
   - Provides accurate UI state management

### Permissions

The app requires the following permissions:
- `FOREGROUND_SERVICE`: Required for running foreground services (Android 9+)
- `POST_NOTIFICATIONS`: Required for showing notifications (Android 13+)
- `CAMERA`: Required for camera access
- `RECORD_AUDIO`: Required for audio streaming
- `INTERNET`: Required for network communication
- `ACCESS_NETWORK_STATE`: Required for network status checking

### WebRTC Architecture

```
Android Device (Server)          Web Browser (Client)
     |                                |
     |-- WebRTC Signaling Server ----|
     |        (HTTP/8080)             |
     |                                |
     |-- Peer Connection ------------|
     |        (WebRTC)                |
     |                                |
     |-- Video Stream ---------------|
     |        (H.264/AAC)             |
```

## Usage

### Starting the Camera Server

1. Launch the SecureCam app
2. Grant camera and microphone permissions when prompted
3. Tap "Start Camera Server" to begin streaming
4. The app will show the WebRTC server URL (e.g., `http://192.168.1.100:8080`)

### Accessing the Camera Stream

1. **From a web browser on the same network:**
   - Open the URL displayed in the app
   - Click "Connect to Camera" button
   - The camera stream will appear in the video element

2. **From any device on the network:**
   - Use the IP address shown in the app
   - Navigate to `http://[IP_ADDRESS]:8080`
   - Follow the same connection process

### Stopping the Server

- Tap "Stop Camera Server" in the app
- The service will stop and the notification will be removed
- All active connections will be terminated

## API Endpoints

The SecureCam server provides several HTTP endpoints for different use cases:

### WebRTC Endpoints

#### `GET /` or `GET /index.html`
- **Purpose**: Main WebRTC client interface
- **Response**: HTML page with WebRTC video player
- **Usage**: Open in browser to view camera stream via WebRTC

#### `POST /offer`
- **Purpose**: WebRTC signaling - handle SDP offer from client
- **Request Body**: JSON with SDP offer
  ```json
  {
    "type": "offer",
    "sdp": "v=0\r\no=- 1234567890 2 IN IP4 127.0.0.1\r\n..."
  }
  ```
- **Response**: JSON with SDP answer
  ```json
  {
    "type": "answer",
    "sdp": "v=0\r\no=- 1234567890 2 IN IP4 127.0.0.1\r\n..."
  }
  ```

#### `POST /ice-candidate`
- **Purpose**: WebRTC signaling - handle ICE candidates
- **Request Body**: JSON with ICE candidate data
- **Response**: JSON confirmation
  ```json
  {
    "type": "success",
    "message": "GetStream Video SDK handles ICE internally"
  }
  ```

### Status and Control Endpoints

#### `GET /status`
- **Purpose**: Get server and camera status
- **Response**: JSON with status information
  ```json
  {
    "status": "ok",
    "message": "SecureCam Open Source WebRTC Server is running",
    "timestamp": 1703123456789,
    "video_ready": true,
    "camera_ready": true
  }
  ```

#### `POST /start-camera`
- **Purpose**: Manually start camera initialization
- **Response**: JSON with operation result
  ```json
  {
    "status": "success",
    "message": "Camera initialization started in background"
  }
  ```

### Home Assistant Integration Endpoints

#### `GET /snapshot`
- **Purpose**: Get current camera frame as JPEG image
- **Response**: JPEG image data
- **Content-Type**: `image/jpeg`
- **Usage**: Home Assistant generic camera integration

#### `GET /stream`
- **Purpose**: MJPEG video stream for Home Assistant
- **Response**: Multipart MJPEG stream
- **Content-Type**: `multipart/x-mixed-replace; boundary=frame`
- **Usage**: Home Assistant generic camera integration

#### `GET /camera-info`
- **Purpose**: Get camera metadata for Home Assistant
- **Response**: JSON with camera information
  ```json
  {
    "name": "SecureCam",
    "model": "Android Camera",
    "manufacturer": "Open Source",
    "camera_ready": true,
    "streaming": true,
    "frame_width": 640,
    "frame_height": 480,
    "snapshot_url": "http://192.168.1.100:8080/snapshot",
    "stream_url": "http://192.168.1.100:8080/stream"
  }
  ```

#### `GET /home-assistant`
- **Purpose**: Home Assistant integration configuration page
- **Response**: HTML page with setup instructions
- **Usage**: Open in browser to get Home Assistant configuration

### Error Responses

All endpoints return standard HTTP error codes:
- `400 Bad Request`: Invalid request format
- `404 Not Found`: Endpoint not found
- `500 Internal Server Error`: Server error

Error responses include JSON with error details:
```json
{
  "status": "error",
  "message": "Error description"
}
```

## Technical Details

### WebRTC Implementation

- **Video Codec**: H.264 (hardware accelerated when available)
- **Audio Codec**: AAC
- **Resolution**: 640x480 (configurable)
- **Frame Rate**: 30 FPS
- **ICE Servers**: Google STUN servers for NAT traversal

### Network Configuration

- **Port**: 8080 (configurable in SimpleHttpServer.java)
- **Protocol**: HTTP for signaling, WebRTC for media
- **CORS**: Enabled for cross-origin requests
- **Security**: Local network only (no external access)

### Performance Considerations

- **Battery Usage**: Camera streaming is battery-intensive
- **Network Bandwidth**: Video quality can be adjusted in WebRTCManager.java
- **Memory Usage**: WebRTC components are properly disposed when service stops

## Customization

### Video Quality Settings

Modify the `initializeCamera()` method in `WebRTCManager.java`:

```java
videoCapturer.startCapture(width, height, frameRate);
```

### Server Port

Change the port in `WebRTCSignalingServer.java`:

```java
private static final int PORT = 8080; // Change to desired port
```

### ICE Servers

Add custom STUN/TURN servers in `WebRTCManager.java`:

```java
iceServers.add(PeerConnection.IceServer.builder("stun:your-stun-server.com:3478").createIceServer());
```

## Building and Running

1. Open the project in Android Studio
2. Ensure you have Java 17 installed (required for Android Gradle Plugin)
3. Build the project: `./gradlew assembleDebug`
4. Install on device: `./gradlew installDebug`
5. Grant permissions and start the camera server

## Troubleshooting

### Common Issues

1. **Permission Denied**: Ensure camera and microphone permissions are granted
2. **Connection Failed**: Check that both devices are on the same network
3. **Video Not Displaying**: Verify WebRTC is supported in the browser
4. **Service Not Starting**: Check logcat for initialization errors

### Debug Information

Enable debug logging by checking logcat with tag filters:
- `ForegroundService`: Service lifecycle events
- `WebRTCManager`: WebRTC connection events
- `WebRTCSignalingServer`: HTTP server events

## Security Notes

- The server is only accessible on the local network
- No authentication is implemented (add for production use)
- Consider implementing HTTPS for secure signaling
- Review camera permissions and data handling for your use case

## Future Enhancements

- Multiple camera support
- Recording functionality
- Motion detection
- Push notifications for events
- Cloud storage integration
- Multi-user access control 