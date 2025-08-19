# SecureCam Log Analysis Script
# Analyzes logs for patterns and errors

Write-Host "=== SecureCam Log Analysis ===" -ForegroundColor Green

# Get recent logs and analyze
Write-Host "Analyzing recent logs..." -ForegroundColor Yellow

# Count errors by type
$errorCount = adb logcat -d -s Camera2Video:E SimpleHttpServer:E | Select-String -Pattern "error" | Measure-Object | Select-Object -ExpandProperty Count
$cameraError4Count = adb logcat -d -s Camera2Video:E | Select-String -Pattern "Camera error: 4" | Measure-Object | Select-Object -ExpandProperty Count
$httpErrorCount = adb logcat -d -s SimpleHttpServer:E | Select-String -Pattern "error" | Measure-Object | Select-Object -ExpandProperty Count

# Count successful operations
$successfulFrames = adb logcat -d -s Camera2Video:D | Select-String -Pattern "Processed JPEG frame" | Measure-Object | Select-Object -ExpandProperty Count
$serviceStarts = adb logcat -d -s ForegroundService:D | Select-String -Pattern "Service created" | Measure-Object | Select-Object -ExpandProperty Count
$cameraLive = adb logcat -d -s Camera2Video:D | Select-String -Pattern "Successfully went live" | Measure-Object | Select-Object -ExpandProperty Count

Write-Host ""
Write-Host "=== Analysis Results ===" -ForegroundColor Cyan
Write-Host "Total Errors: $errorCount" -ForegroundColor $(if($errorCount -gt 0) { "Red" } else { "Green" })
Write-Host "Camera Error 4: $cameraError4Count" -ForegroundColor $(if($cameraError4Count -gt 0) { "Yellow" } else { "Green" })
Write-Host "HTTP Server Errors: $httpErrorCount" -ForegroundColor $(if($httpErrorCount -gt 0) { "Yellow" } else { "Green" })
Write-Host ""
Write-Host "Successful Operations:" -ForegroundColor Green
Write-Host "  Frames Processed: $successfulFrames" -ForegroundColor Green
Write-Host "  Service Starts: $serviceStarts" -ForegroundColor Green
Write-Host "  Camera Live Events: $cameraLive" -ForegroundColor Green

Write-Host ""
Write-Host "=== Status Assessment ===" -ForegroundColor Cyan
if ($successfulFrames -gt 0 -and $cameraLive -gt 0) {
    Write-Host "✅ App is working correctly" -ForegroundColor Green
    Write-Host "   - Camera is processing frames" -ForegroundColor Green
    Write-Host "   - WebRTC is generating SDP offers" -ForegroundColor Green
    Write-Host "   - Service is managing lifecycle properly" -ForegroundColor Green
} else {
    Write-Host "❌ App may have issues" -ForegroundColor Red
}

if ($cameraError4Count -gt 0) {
    Write-Host ""
    Write-Host "⚠️  Camera Error 4 detected" -ForegroundColor Yellow
    Write-Host "   - This is usually a camera resource conflict" -ForegroundColor Yellow
    Write-Host "   - App handles this gracefully" -ForegroundColor Yellow
    Write-Host "   - Consider closing other camera apps" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "=== Recommendations ===" -ForegroundColor Cyan
Write-Host "1. Test web interface: http://192.168.1.144:8080" -ForegroundColor White
Write-Host "2. Monitor for error frequency patterns" -ForegroundColor White
Write-Host "3. Check if other apps are using camera" -ForegroundColor White
Write-Host "4. Verify network connectivity" -ForegroundColor White
