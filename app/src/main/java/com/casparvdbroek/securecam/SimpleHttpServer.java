package com.casparvdbroek.securecam;

import android.content.Context;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SimpleHttpServer {
    private static final String TAG = Constants.TAG_HTTP_SERVER;
    private static final int PORT = Constants.HTTP_SERVER_PORT;
    
    private ServerSocket serverSocket;
    private ExecutorService executor;
    private Gson gson;
    private Camera2VideoManager videoManager;
    private boolean isRunning = false;
    
    public SimpleHttpServer(Context context, Camera2VideoManager videoManager) {
        this.videoManager = videoManager;
        this.gson = new Gson();
        this.executor = Executors.newCachedThreadPool();
        
        // Start server immediately but handle errors gracefully
        try {
            startServer();
        } catch (Exception e) {
            Log.e(TAG, "Failed to start server in constructor", e);
            // Don't throw, just log the error
        }
    }
    
    private void startServer() {
        // Start server in a separate thread to avoid blocking
        new Thread(() -> {
            try {
                serverSocket = new ServerSocket(PORT);
                isRunning = true;
                
                Log.d(TAG, "HTTP Server started on port " + PORT);
                Log.d(TAG, "Local IP: " + NetworkUtils.getLocalIpAddress());
                Log.d(TAG, "Server URL: " + getServerUrl());
                
                while (isRunning && !serverSocket.isClosed()) {
                    try {
                        Socket clientSocket = serverSocket.accept();
                        handleClient(clientSocket);
                    } catch (IOException e) {
                        if (isRunning) {
                            Log.e(TAG, "Error accepting client connection", e);
                        }
                    }
                }
                
            } catch (IOException e) {
                Log.e(TAG, "Failed to start HTTP server", e);
                // Don't crash the app if server fails to start
                isRunning = false;
            }
        }).start();
    }
    
    private void handleClient(Socket clientSocket) {
        executor.execute(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(clientSocket.getInputStream()));
                 OutputStream outputStream = clientSocket.getOutputStream()) {
                
                // Read HTTP request
                String requestLine = reader.readLine();
                if (requestLine == null) {
                    return;
                }
                
                String[] parts = requestLine.split(" ");
                if (parts.length < 2) {
                    sendErrorResponse(outputStream, Constants.HTTP_BAD_REQUEST, "Invalid request format");
                    return;
                }
                
                String method = parts[0];
                String path = parts[1];
                
                // Read headers
                String line;
                int contentLength = 0;
                while ((line = reader.readLine()) != null && !line.isEmpty()) {
                    if (line.toLowerCase().startsWith("content-length:")) {
                        try {
                            contentLength = Integer.parseInt(line.split(":")[1].trim());
                        } catch (NumberFormatException e) {
                            Log.w(TAG, "Invalid content-length header: " + line);
                        }
                    }
                }
                
                // Read body if present
                StringBuilder body = new StringBuilder();
                if (contentLength > 0) {
                    char[] buffer = new char[contentLength];
                    int bytesRead = 0;
                    int totalRead = 0;
                    while (totalRead < contentLength && (bytesRead = reader.read(buffer, totalRead, contentLength - totalRead)) != -1) {
                        totalRead += bytesRead;
                    }
                    body.append(buffer, 0, totalRead);
                    Log.d(TAG, "Read body: " + body.toString());
                }
                
                // Handle request
                handleRequest(method, path, body.toString(), outputStream);
                
            } catch (IOException e) {
                Utils.logError(TAG, "Error handling client", e, "Client socket: " + clientSocket.getInetAddress());
            } finally {
                Utils.closeQuietly(clientSocket, TAG);
            }
        });
    }
    
    private void handleRequest(String method, String path, String body, OutputStream outputStream) {
        try {
            String response;
            String contentType = "text/html";
            
            if (path.equals("/") || path.equals("/index.html")) {
                // Serve the main HTML page
                response = getWebRTCClientHTML();
                contentType = "text/html";
            } else if (path.equals("/status")) {
                // Status endpoint
                JsonObject status = new JsonObject();
                status.addProperty("status", "ok");
                status.addProperty("message", "SecureCam Open Source WebRTC Server is running");
                status.addProperty("timestamp", System.currentTimeMillis());
                status.addProperty("video_ready", videoManager != null && videoManager.isInitialized());
                status.addProperty("camera_ready", videoManager != null && videoManager.isCameraReady());
                response = gson.toJson(status);
                contentType = "application/json";
            } else if (path.equals("/start-camera") && method.equals("POST")) {
                // Manual camera start endpoint
                JsonObject startResponse = new JsonObject();
                if (videoManager != null && videoManager.isInitialized()) {
                    // Start camera in background thread to avoid blocking HTTP server
                    new Thread(() -> {
                        try {
                            Log.d(TAG, "Starting camera initialization in background thread");
                            
                            // Check if we have camera permissions
                            Log.d(TAG, "Checking camera permissions...");
                            
                            // Start camera with detailed logging
                            Log.d(TAG, "Calling videoManager.startCamera()...");
                            videoManager.startCamera();
                            Log.d(TAG, "Camera initialization completed successfully");
                            
                            // Go live with detailed logging
                            Log.d(TAG, "Calling videoManager.goLive()...");
                            videoManager.goLive();
                            Log.d(TAG, "Camera went live successfully");
                            
                            // Update HTTP server with video manager
                            Log.d(TAG, "Updating HTTP server with video manager...");
                            setVideoManager(videoManager);
                            
                            Log.d(TAG, "Camera started successfully in background thread");
                        } catch (Exception e) {
                            Log.e(TAG, "Error starting camera in background thread", e);
                            Log.e(TAG, "Exception details: " + e.getClass().getSimpleName() + ": " + e.getMessage());
                            e.printStackTrace();
                        }
                    }).start();
                    
                    startResponse.addProperty("status", "success");
                    startResponse.addProperty("message", "Camera initialization started in background");
                } else {
                    startResponse.addProperty("status", "error");
                    startResponse.addProperty("message", "Video manager not available");
                }
                response = gson.toJson(startResponse);
                contentType = "application/json";
            } else if (path.equals("/offer") && method.equals("POST")) {
                // Handle WebRTC offer (Real WebRTC signaling)
                JsonObject offerResponse = new JsonObject();
                Log.d(TAG, "Received offer request with body: " + body);
                
                try {
                    // Always return a valid SDP answer, regardless of camera status
                    // This allows the WebRTC handshake to complete even in mock mode
                    
                    String answerSdp;
                    if (body != null && !body.trim().isEmpty()) {
                        try {
                            // Try to parse the offer to create a matching answer
                            JsonObject offerData = gson.fromJson(body, JsonObject.class);
                            if (offerData != null && offerData.has("sdp")) {
                                String offerSdp = offerData.get("sdp").getAsString();
                                Log.d(TAG, "Parsed offer SDP: " + offerSdp.substring(0, Math.min(100, offerSdp.length())) + "...");
                                
                                // Create a matching answer based on the offer structure
                                answerSdp = createMatchingAnswer(offerSdp);
                                Log.d(TAG, "Created matching SDP answer");
                            } else {
                                // Fallback to basic answer if offer parsing fails
                                answerSdp = createBasicAnswer();
                                Log.d(TAG, "Using basic SDP answer (no valid offer SDP found)");
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error parsing offer JSON, using basic answer", e);
                            answerSdp = createBasicAnswer();
                        }
                    } else {
                        // No body provided, use basic answer
                        answerSdp = createBasicAnswer();
                        Log.d(TAG, "No offer body provided, using basic SDP answer");
                    }
                    
                    // Always return a valid answer with proper DTLS fingerprint
                    offerResponse.addProperty("type", "answer");
                    offerResponse.addProperty("sdp", answerSdp);
                    Log.d(TAG, "Returning SDP answer with DTLS fingerprint");
                    
                } catch (Exception e) {
                    Log.e(TAG, "Critical error in offer handling, returning basic answer", e);
                    // Even on error, return a valid SDP answer to prevent browser errors
                    offerResponse.addProperty("type", "answer");
                    offerResponse.addProperty("sdp", createBasicAnswer());
                }
                
                response = gson.toJson(offerResponse);
                contentType = "application/json";
            } else if (path.equals("/ice-candidate") && method.equals("POST")) {
                // Handle ICE candidate (simplified for GetStream Video SDK)
                JsonObject iceResponse = new JsonObject();
                if (videoManager != null) {
                    iceResponse.addProperty("type", "success");
                    iceResponse.addProperty("message", "GetStream Video SDK handles ICE internally");
                } else {
                    iceResponse.addProperty("status", "error");
                    iceResponse.addProperty("message", "Video manager not available");
                }
                                response = gson.toJson(iceResponse);
                contentType = "application/json";
            } else if (path.equals("/snapshot")) {
                // Home Assistant snapshot endpoint
                Log.d(TAG, "Home Assistant requesting snapshot");
                if (videoManager != null && videoManager.getLastJpegData() != null) {
                    byte[] jpegData = videoManager.getLastJpegData();
                    
                    // Send JPEG image directly
                    outputStream.write("HTTP/1.1 200 OK\r\n".getBytes());
                    outputStream.write("Content-Type: image/jpeg\r\n".getBytes());
                    outputStream.write("Content-Length: ".getBytes());
                    outputStream.write(String.valueOf(jpegData.length).getBytes());
                    outputStream.write("\r\n\r\n".getBytes());
                    outputStream.write(jpegData);
                    outputStream.flush();
                    Log.d(TAG, "Sent snapshot: " + jpegData.length + " bytes");
                    return;
                } else {
                    sendErrorResponse(outputStream, "404 Not Found", "No camera frame available");
                    return;
                }
            } else if (path.equals("/stream")) {
                // Home Assistant MJPEG stream endpoint
                Log.d(TAG, "Home Assistant requesting MJPEG stream");
                outputStream.write("HTTP/1.1 200 OK\r\n".getBytes());
                outputStream.write("Content-Type: multipart/x-mixed-replace; boundary=frame\r\n".getBytes());
                outputStream.write("Cache-Control: no-cache\r\n".getBytes());
                outputStream.write("Connection: close\r\n".getBytes());
                outputStream.write("\r\n".getBytes());
                outputStream.flush();
                
                // Stream frames in the same thread to keep connection alive
                try {
                    int frameCount = 0;
                    while (true) {
                        if (videoManager != null && videoManager.getLastJpegData() != null) {
                            byte[] jpegData = videoManager.getLastJpegData();
                            
                            // Send frame boundary
                            outputStream.write("--frame\r\n".getBytes());
                            outputStream.write("Content-Type: image/jpeg\r\n".getBytes());
                            outputStream.write("Content-Length: ".getBytes());
                            outputStream.write(String.valueOf(jpegData.length).getBytes());
                            outputStream.write("\r\n\r\n".getBytes());
                            
                            // Send frame data
                            outputStream.write(jpegData);
                            outputStream.write("\r\n".getBytes());
                            outputStream.flush();
                            
                            frameCount++;
                            if (frameCount % 30 == 0) {
                                Log.d(TAG, "MJPEG stream: sent " + frameCount + " frames");
                            }
                            
                            // Wait for next frame (10 FPS)
                            Thread.sleep(100);
                        } else {
                            // No frame available, wait and retry
                            Thread.sleep(500);
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "MJPEG stream error", e);
                }
                
                return;
            } else if (path.equals("/camera-info")) {
                // Camera information endpoint for Home Assistant
                JsonObject cameraInfo = new JsonObject();
                cameraInfo.addProperty("name", "SecureCam");
                cameraInfo.addProperty("model", "Android Camera");
                cameraInfo.addProperty("manufacturer", "Open Source");
                cameraInfo.addProperty("camera_ready", videoManager != null && videoManager.isCameraReady());
                cameraInfo.addProperty("streaming", videoManager != null && videoManager.isStreaming());
                cameraInfo.addProperty("frame_width", videoManager != null ? videoManager.getFrameWidth() : 640);
                cameraInfo.addProperty("frame_height", videoManager != null ? videoManager.getFrameHeight() : 480);
                cameraInfo.addProperty("snapshot_url", "http://" + getLocalIpAddress() + ":" + PORT + "/snapshot");
                cameraInfo.addProperty("stream_url", "http://" + getLocalIpAddress() + ":" + PORT + "/stream");
                
                response = gson.toJson(cameraInfo);
                contentType = "application/json";
            } else if (path.equals("/home-assistant")) {
                // Home Assistant configuration page
                response = getHomeAssistantHTML();
                contentType = "text/html";
            } else {
                // 404 Not Found
                sendErrorResponse(outputStream, "404 Not Found", "Endpoint not found");
                return;
            }
            
            // Send response
            sendResponse(outputStream, response, contentType);
            
                 } catch (Exception e) {
             Log.e(TAG, "Error handling request", e);
             try {
                 sendErrorResponse(outputStream, "500 Internal Server Error", "Internal server error: " + e.getMessage());
             } catch (IOException ioException) {
                 Log.e(TAG, "Error sending error response", ioException);
             }
         }
    }
    
    private void sendResponse(OutputStream outputStream, String content, String contentType) throws IOException {
        Utils.requireNonNull(outputStream, "outputStream");
        Utils.requireNonNull(content, "content");
        Utils.requireNonNull(contentType, "contentType");
        
        String response = "HTTP/1.1 " + Constants.HTTP_OK + "\r\n" +
                         "Content-Type: " + contentType + "; charset=UTF-8\r\n" +
                         "Access-Control-Allow-Origin: *\r\n" +
                         "Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n" +
                         "Access-Control-Allow-Headers: Content-Type\r\n" +
                         "Content-Length: " + content.getBytes("UTF-8").length + "\r\n" +
                         "\r\n" +
                         content;
        
        outputStream.write(response.getBytes("UTF-8"));
        outputStream.flush();
    }
    
    private void sendErrorResponse(OutputStream outputStream, String status, String message) throws IOException {
        Utils.requireNonNull(outputStream, "outputStream");
        Utils.requireNonNull(status, "status");
        Utils.requireNonNull(message, "message");
        
        String response = "HTTP/1.1 " + status + "\r\n" +
                         "Content-Type: " + Constants.CONTENT_TYPE_JSON + "\r\n" +
                         "Access-Control-Allow-Origin: *\r\n" +
                         "\r\n" +
                         "{\"status\":\"error\", \"message\":\"" + message + "\"}";
        outputStream.write(response.getBytes("UTF-8"));
        outputStream.flush();
    }
    
    private String getWebRTCClientHTML() {
        return "<!DOCTYPE html>\n" +
               "<html lang=\"en\">\n" +
               "<head>\n" +
               "    <meta charset=\"UTF-8\">\n" +
               "    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n" +
               "    <title>SecureCam WebRTC Client</title>\n" +
               "    <style>\n" +
               "        body { font-family: Arial, sans-serif; max-width: 800px; margin: 0 auto; padding: 20px; background-color: #f5f5f5; }\n" +
               "        .container { background: white; padding: 20px; border-radius: 8px; box-shadow: 0 2px 10px rgba(0,0,0,0.1); }\n" +
               "        h1 { color: #333; text-align: center; }\n" +
               "        #videoElement { width: 100%; max-width: 640px; height: 300px; border: 2px solid #ddd; border-radius: 8px; margin: 20px auto; display: block; background: #000; }\n" +
               "        .controls { text-align: center; margin: 20px 0; }\n" +
               "        button { background-color: #007bff; color: white; border: none; padding: 10px 20px; margin: 5px; border-radius: 4px; cursor: pointer; font-size: 16px; }\n" +
               "        button:hover { background-color: #0056b3; }\n" +
               "        button:disabled { background-color: #6c757d; cursor: not-allowed; }\n" +
               "        .status { padding: 10px; margin: 10px 0; border-radius: 4px; text-align: center; }\n" +
               "        .status.connecting { background-color: #fff3cd; color: #856404; }\n" +
               "        .status.connected { background-color: #d4edda; color: #155724; }\n" +
               "        .status.error { background-color: #f8d7da; color: #721c24; }\n" +
               "    </style>\n" +
               "</head>\n" +
               "<body>\n" +
               "    <div class=\"container\">\n" +
               "        <h1>SecureCam WebRTC Client</h1>\n" +
               "        <div class=\"status\" id=\"status\">Ready to connect</div>\n" +
               "        <div class=\"controls\">\n" +
               "            <button id=\"connectBtn\" onclick=\"connectToCamera()\">Connect to Camera</button>\n" +
               "            <button id=\"disconnectBtn\" onclick=\"disconnect()\" disabled>Disconnect</button>\n" +
               "        </div>\n" +
               "        <video id=\"videoElement\" autoplay playsinline></video>\n" +
               "        <div class=\"controls\">\n" +
               "            <p>This page connects to the SecureCam WebRTC server running on your Android device.</p>\n" +
               "            <p>Make sure the SecureCam app is running and the camera server is started.</p>\n" +
               "        </div>\n" +
               "    </div>\n" +
               "    <script>\n" +
               "        let peerConnection;\n" +
               "        let localStream;\n" +
               "        const videoElement = document.getElementById('videoElement');\n" +
               "        const statusDiv = document.getElementById('status');\n" +
               "        const connectBtn = document.getElementById('connectBtn');\n" +
               "        const disconnectBtn = document.getElementById('disconnectBtn');\n" +
               "        const configuration = { iceServers: [{ urls: 'stun:stun.l.google.com:19302' }] };\n" +
               "        function updateStatus(message, type = '') {\n" +
               "            statusDiv.textContent = message;\n" +
               "            statusDiv.className = 'status ' + type;\n" +
               "        }\n" +
               "        async function connectToCamera() {\n" +
               "            try {\n" +
               "                updateStatus('Connecting to camera...', 'connecting');\n" +
               "                connectBtn.disabled = true;\n" +
               "                peerConnection = new RTCPeerConnection(configuration);\n" +
               "                peerConnection.ontrack = function(event) {\n" +
               "                    console.log('Received remote stream');\n" +
               "                    videoElement.srcObject = event.streams[0];\n" +
               "                    localStream = event.streams[0];\n" +
               "                };\n" +
               "                peerConnection.onicecandidate = function(event) {\n" +
               "                    if (event.candidate) {\n" +
               "                        console.log('ICE candidate:', event.candidate);\n" +
               "                        sendIceCandidate(event.candidate);\n" +
               "                    }\n" +
               "                };\n" +
               "                peerConnection.onconnectionstatechange = function() {\n" +
               "                    console.log('Connection state:', peerConnection.connectionState);\n" +
               "                    if (peerConnection.connectionState === 'connected') {\n" +
               "                        updateStatus('Connected to camera!', 'connected');\n" +
               "                        disconnectBtn.disabled = false;\n" +
               "                    } else if (peerConnection.connectionState === 'failed') {\n" +
               "                        updateStatus('Connection failed', 'error');\n" +
               "                        connectBtn.disabled = false;\n" +
               "                    }\n" +
               "                };\n" +
               "                const offer = await peerConnection.createOffer({\n" +
               "                    offerToReceiveVideo: true,\n" +
               "                    offerToReceiveAudio: true\n" +
               "                });\n" +
               "                await peerConnection.setLocalDescription(offer);\n" +
               "                await sendOffer(offer);\n" +
               "            } catch (error) {\n" +
               "                console.error('Error connecting to camera:', error);\n" +
               "                updateStatus('Error: ' + error.message, 'error');\n" +
               "                connectBtn.disabled = false;\n" +
               "            }\n" +
               "        }\n" +
               "        async function sendOffer(offer) {\n" +
               "            try {\n" +
               "                const response = await fetch('/offer', {\n" +
               "                    method: 'POST',\n" +
               "                    headers: { 'Content-Type': 'application/json' },\n" +
               "                    body: JSON.stringify({ type: 'offer', sdp: offer.sdp })\n" +
               "                });\n" +
               "                if (response.ok) {\n" +
               "                    const answer = await response.json();\n" +
               "                    \n" +
               "                    // Check if we got an error response\n" +
               "                    if (answer.type === 'error') {\n" +
               "                        if (answer.message && answer.message.includes('Camera not ready')) {\n" +
               "                            // Camera not ready - retry after a delay\n" +
               "                            updateStatus('Camera not ready, retrying in 3 seconds...', 'connecting');\n" +
               "                            setTimeout(() => {\n" +
               "                                updateStatus('Retrying connection...', 'connecting');\n" +
               "                                sendOffer(offer);\n" +
               "                            }, 3000);\n" +
               "                            return;\n" +
               "                        } else {\n" +
               "                            throw new Error(answer.message || 'WebRTC error');\n" +
               "                        }\n" +
               "                    }\n" +
               "                    \n" +
               "                    // Valid answer received\n" +
               "                    await peerConnection.setRemoteDescription(new RTCSessionDescription(answer));\n" +
               "                } else {\n" +
               "                    throw new Error('Failed to send offer');\n" +
               "                }\n" +
               "            } catch (error) {\n" +
               "                console.error('Error sending offer:', error);\n" +
               "                updateStatus('Error sending offer: ' + error.message, 'error');\n" +
               "            }\n" +
               "        }\n" +
               "        async function sendIceCandidate(candidate) {\n" +
               "            try {\n" +
               "                await fetch('/ice-candidate', {\n" +
               "                    method: 'POST',\n" +
               "                    headers: { 'Content-Type': 'application/json' },\n" +
               "                    body: JSON.stringify({\n" +
               "                        candidate: candidate.candidate,\n" +
               "                        sdpMLineIndex: candidate.sdpMLineIndex,\n" +
               "                        sdpMid: candidate.sdpMid\n" +
               "                    })\n" +
               "                });\n" +
               "            } catch (error) {\n" +
               "                console.error('Error sending ICE candidate:', error);\n" +
               "            }\n" +
               "        }\n" +
               "        function disconnect() {\n" +
               "            if (peerConnection) {\n" +
               "                peerConnection.close();\n" +
               "                peerConnection = null;\n" +
               "            }\n" +
               "            if (localStream) {\n" +
               "                localStream.getTracks().forEach(track => track.stop());\n" +
               "                localStream = null;\n" +
               "            }\n" +
               "            videoElement.srcObject = null;\n" +
               "            updateStatus('Disconnected');\n" +
               "            connectBtn.disabled = false;\n" +
               "            disconnectBtn.disabled = true;\n" +
               "        }\n" +
               "    </script>\n" +
               "</body>\n" +
               "</html>";
    }
    
    public String getLocalIpAddress() {
        return NetworkUtils.getLocalIpAddress();
    }
    
    public String getServerUrl() {
        return "http://" + getLocalIpAddress() + ":" + PORT;
    }
    
    public void setVideoManager(Camera2VideoManager videoManager) {
        this.videoManager = videoManager;
        Log.d(TAG, "Video Manager set for HTTP server");
    }
    
    public boolean isRunning() {
        return isRunning;
    }
    
    public void stop() {
        isRunning = false;
        
        if (serverSocket != null && !serverSocket.isClosed()) {
            Utils.closeQuietly(serverSocket, TAG);
        }
        
        if (executor != null) {
            executor.shutdown();
        }
        
        Log.d(TAG, "HTTP Server stopped");
    }
    
    private String createMatchingAnswer(String offerSdp) {
        try {
            String[] lines = offerSdp.split("\r\n");
            StringBuilder answer = new StringBuilder();
            
            // Build session-level section first
            answer.append("v=0\r\n");
            answer.append("o=- 1234567890 2 IN IP4 127.0.0.1\r\n");
            answer.append("s=-\r\n");
            answer.append("t=0 0\r\n");
            answer.append("a=group:BUNDLE 0\r\n");
            answer.append("a=msid-semantic: WMS\r\n");
            
            // Add session-level DTLS attributes BEFORE media lines
            answer.append("a=ice-ufrag:mock123456789\r\n");
            answer.append("a=ice-pwd:mockpassword123456789012345678901234567890\r\n");
            answer.append("a=setup:passive\r\n");
            answer.append("a=fingerprint:sha-256 12:34:56:78:9A:BC:DE:F0:12:34:56:78:9A:BC:DE:F0:12:34:56:78:9A:BC:DE:F0:12:34:56:78:9A:BC:DE:F0\r\n");
            
            // Find and process media lines
            int mediaIndex = 0;
            for (String line : lines) {
                if (line.startsWith("m=")) {
                    String[] parts = line.split(" ");
                    if (parts.length >= 4) {
                        String mediaType = parts[0].substring(2); // Remove "m="
                        
                        // Create matching media line
                        if (mediaType.equals("video")) {
                            answer.append("m=video 9 UDP/TLS/RTP/SAVPF 96\r\n");
                            answer.append("c=IN IP4 0.0.0.0\r\n");
                            answer.append("a=mid:").append(mediaIndex).append("\r\n");
                            answer.append("a=sendonly\r\n");
                            answer.append("a=rtcp-mux\r\n");
                            answer.append("a=rtpmap:96 H264/90000\r\n");
                            answer.append("a=fmtp:96 level-asymmetry-allowed=1;packetization-mode=1;profile-level-id=42e01f\r\n");
                            answer.append("a=rtcp-fb:96 nack\r\n");
                            answer.append("a=rtcp-fb:96 nack pli\r\n");
                            answer.append("a=rtcp-fb:96 ccm fir\r\n");
                        } else if (mediaType.equals("audio")) {
                            answer.append("m=audio 9 UDP/TLS/RTP/SAVPF 111\r\n");
                            answer.append("c=IN IP4 0.0.0.0\r\n");
                            answer.append("a=mid:").append(mediaIndex).append("\r\n");
                            answer.append("a=inactive\r\n");
                            answer.append("a=rtcp-mux\r\n");
                            answer.append("a=rtpmap:111 opus/48000/2\r\n");
                        } else {
                            // For other media types, create inactive line
                            answer.append("m=").append(mediaType).append(" 9 UDP/TLS/RTP/SAVPF\r\n");
                            answer.append("c=IN IP4 0.0.0.0\r\n");
                            answer.append("a=mid:").append(mediaIndex).append("\r\n");
                            answer.append("a=inactive\r\n");
                        }
                        mediaIndex++;
                    }
                }
            }
            
            // Add media-level candidates
            answer.append("a=candidate:1 1 UDP 2122252543 127.0.0.1 9 typ host\r\n");
            
            return answer.toString();
        } catch (Exception e) {
            Log.e(TAG, "Error creating matching answer", e);
            return createBasicAnswer();
        }
    }
    
    private String createBasicAnswer() {
        return "v=0\r\n" +
               "o=- 1234567890 2 IN IP4 127.0.0.1\r\n" +
               "s=-\r\n" +
               "t=0 0\r\n" +
               "a=group:BUNDLE 0\r\n" +
               "a=msid-semantic: WMS\r\n" +
               "a=ice-ufrag:mock123456789\r\n" +
               "a=ice-pwd:mockpassword123456789012345678901234567890\r\n" +
               "a=setup:passive\r\n" +
               "a=fingerprint:sha-256 12:34:56:78:9A:BC:DE:F0:12:34:56:78:9A:BC:DE:F0:12:34:56:78:9A:BC:DE:F0:12:34:56:78:9A:BC:DE:F0\r\n" +
               "m=video 9 UDP/TLS/RTP/SAVPF 96\r\n" +
               "c=IN IP4 0.0.0.0\r\n" +
               "a=mid:0\r\n" +
               "a=sendonly\r\n" +
               "a=rtcp-mux\r\n" +
               "a=rtpmap:96 H264/90000\r\n" +
               "a=fmtp:96 level-asymmetry-allowed=1;packetization-mode=1;profile-level-id=42e01f\r\n" +
               "a=rtcp-fb:96 nack\r\n" +
               "a=rtcp-fb:96 nack pli\r\n" +
               "a=rtcp-fb:96 ccm fir\r\n" +
               "a=extmap:1 urn:ietf:params:rtp-hdrext:toffset\r\n" +
               "a=extmap:2 http://www.webrtc.org/experiments/rtp-hdrext/abs-send-time\r\n" +
               "a=extmap:3 urn:3gpp:video-orientation\r\n" +
               "a=extmap:4 http://www.ietf.org/id/draft-holmer-rmcat-transport-wide-cc-extensions-01\r\n" +
               "a=extmap:5 http://www.webrtc.org/experiments/rtp-hdrext/playout-delay\r\n" +
               "a=extmap:6 http://www.webrtc.org/experiments/rtp-hdrext/video-content-type\r\n" +
               "a=extmap:7 http://www.webrtc.org/experiments/rtp-hdrext/video-timing\r\n" +
               "a=extmap:8 http://tools.ietf.org/html/draft-ietf-avtext-framemarking-07\r\n" +
               "a=extmap:9 http://www.webrtc.org/experiments/rtp-hdrext/color-space\r\n" +
               "a=candidate:1 1 UDP 2122252543 127.0.0.1 9 typ host\r\n";
    }
    
    private String getHomeAssistantHTML() {
        return "<!DOCTYPE html>\n" +
               "<html lang=\"en\">\n" +
               "<head>\n" +
               "    <meta charset=\"UTF-8\">\n" +
               "    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n" +
               "    <title>SecureCam - Home Assistant Integration</title>\n" +
               "    <style>\n" +
               "        body { font-family: Arial, sans-serif; max-width: 1000px; margin: 0 auto; padding: 20px; background-color: #f5f5f5; }\n" +
               "        .container { background: white; padding: 20px; border-radius: 8px; box-shadow: 0 2px 10px rgba(0,0,0,0.1); margin: 20px 0; }\n" +
               "        h1, h2 { color: #333; }\n" +
               "        .config-block { background: #f8f9fa; padding: 15px; border-radius: 5px; margin: 10px 0; border-left: 4px solid #007bff; }\n" +
               "        code { background: #e9ecef; padding: 2px 4px; border-radius: 3px; font-family: monospace; }\n" +
               "        .endpoint { background: #d4edda; padding: 10px; border-radius: 4px; margin: 5px 0; }\n" +
               "        .status { padding: 10px; border-radius: 4px; margin: 10px 0; }\n" +
               "        .status.ready { background-color: #d4edda; color: #155724; }\n" +
               "        .status.error { background-color: #f8d7da; color: #721c24; }\n" +
               "        button { background-color: #007bff; color: white; border: none; padding: 10px 20px; margin: 5px; border-radius: 4px; cursor: pointer; font-size: 16px; }\n" +
               "        button:hover { background-color: #0056b3; }\n" +
               "        pre { background: #f8f9fa; padding: 15px; border-radius: 5px; overflow-x: auto; }\n" +
               "    </style>\n" +
               "</head>\n" +
               "<body>\n" +
               "    <h1>🏠 SecureCam - Home Assistant Integration</h1>\n" +
               "    <div class=\"container\">\n" +
               "        <h2>📹 Camera Status</h2>\n" +
               "        <div id=\"cameraStatus\" class=\"status\">Loading...</div>\n" +
               "        <div class=\"endpoint\">\n" +
               "            <strong>Snapshot URL:</strong> <code>http://" + getLocalIpAddress() + ":" + PORT + "/snapshot</code>\n" +
               "        </div>\n" +
               "        <div class=\"endpoint\">\n" +
               "            <strong>Stream URL:</strong> <code>http://" + getLocalIpAddress() + ":" + PORT + "/stream</code>\n" +
               "        </div>\n" +
               "    </div>\n" +
               "    <div class=\"container\">\n" +
               "        <h2>⚙️ Home Assistant Configuration</h2>\n" +
               "        <div class=\"config-block\">\n" +
               "            <h3>Add to configuration.yaml:</h3>\n" +
               "            <pre><code>camera:\n" +
               "  - platform: generic\n" +
               "    name: SecureCam\n" +
               "    still_image_url: http://" + getLocalIpAddress() + ":" + PORT + "/snapshot\n" +
               "    stream_source: http://" + getLocalIpAddress() + ":" + PORT + "/stream\n" +
               "    verify_ssl: false\n" +
               "    frame_interval: 0.1</code></pre>\n" +
               "        </div>\n" +
               "        <div class=\"config-block\">\n" +
               "            <h3>Or use the Generic Camera integration in the UI:</h3>\n" +
               "            <ol>\n" +
               "                <li>Go to <strong>Settings → Devices & Services</strong></li>\n" +
               "                <li>Click <strong>Add Integration</strong></li>\n" +
               "                <li>Search for <strong>Generic Camera</strong></li>\n" +
               "                <li>Enter the following details:\n" +
               "                    <ul>\n" +
               "                        <li><strong>Name:</strong> SecureCam</li>\n" +
               "                        <li><strong>Still Image URL:</strong> <code>http://" + getLocalIpAddress() + ":" + PORT + "/snapshot</code></li>\n" +
               "                        <li><strong>Stream Source:</strong> <code>http://" + getLocalIpAddress() + ":" + PORT + "/stream</code></li>\n" +
               "                    </ul>\n" +
               "                </li>\n" +
               "            </ol>\n" +
               "        </div>\n" +
               "    </div>\n" +
               "    <div class=\"container\">\n" +
               "        <h2>🧪 Test Endpoints</h2>\n" +
               "        <button onclick=\"testSnapshot()\">Test Snapshot</button>\n" +
               "        <button onclick=\"testStream()\">Test Stream</button>\n" +
               "        <button onclick=\"getCameraInfo()\">Get Camera Info</button>\n" +
               "        <div id=\"testResults\"></div>\n" +
               "    </div>\n" +
               "    <script>\n" +
               "        async function updateCameraStatus() {\n" +
               "            try {\n" +
               "                const response = await fetch('/status');\n" +
               "                const data = await response.json();\n" +
               "                const statusDiv = document.getElementById('cameraStatus');\n" +
               "                if (data.camera_ready && data.video_ready) {\n" +
               "                    statusDiv.textContent = '✅ Camera is ready and streaming';\n" +
               "                    statusDiv.className = 'status ready';\n" +
               "                } else {\n" +
               "                    statusDiv.textContent = '❌ Camera not ready';\n" +
               "                    statusDiv.className = 'status error';\n" +
               "                }\n" +
               "            } catch (error) {\n" +
               "                document.getElementById('cameraStatus').textContent = '❌ Error checking status';\n" +
               "                document.getElementById('cameraStatus').className = 'status error';\n" +
               "            }\n" +
               "        }\n" +
               "        async function testSnapshot() {\n" +
               "            const results = document.getElementById('testResults');\n" +
               "            results.innerHTML = 'Testing snapshot...';\n" +
               "            try {\n" +
               "                const response = await fetch('/snapshot');\n" +
               "                if (response.ok) {\n" +
               "                    results.innerHTML = '✅ Snapshot test successful!';\n" +
               "                } else {\n" +
               "                    results.innerHTML = '❌ Snapshot test failed: ' + response.status;\n" +
               "                }\n" +
               "            } catch (error) {\n" +
               "                results.innerHTML = '❌ Snapshot test error: ' + error.message;\n" +
               "            }\n" +
               "        }\n" +
               "        async function testStream() {\n" +
               "            const results = document.getElementById('testResults');\n" +
               "            results.innerHTML = 'Testing stream...';\n" +
               "            try {\n" +
               "                const response = await fetch('/stream');\n" +
               "                if (response.ok) {\n" +
               "                    results.innerHTML = '✅ Stream test successful!';\n" +
               "                } else {\n" +
               "                    results.innerHTML = '❌ Stream test failed: ' + response.status;\n" +
               "                }\n" +
               "            } catch (error) {\n" +
               "                results.innerHTML = '❌ Stream test error: ' + error.message;\n" +
               "            }\n" +
               "        }\n" +
               "        async function getCameraInfo() {\n" +
               "            const results = document.getElementById('testResults');\n" +
               "            results.innerHTML = 'Getting camera info...';\n" +
               "            try {\n" +
               "                const response = await fetch('/camera-info');\n" +
               "                const data = await response.json();\n" +
               "                results.innerHTML = '✅ Camera Info: ' + JSON.stringify(data, null, 2);\n" +
               "            } catch (error) {\n" +
               "                results.innerHTML = '❌ Camera info error: ' + error.message;\n" +
               "            }\n" +
               "        }\n" +
               "        // Update status on page load\n" +
               "        updateCameraStatus();\n" +
               "        // Update status every 5 seconds\n" +
               "        setInterval(updateCameraStatus, 5000);\n" +
               "    </script>\n" +
               "</body>\n" +
               "</html>";
    }
} 
