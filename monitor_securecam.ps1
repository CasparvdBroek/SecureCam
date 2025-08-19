# SecureCam Log Monitoring Script
# Run this script to monitor SecureCam app logs in real-time

Write-Host "=== SecureCam Log Monitor ===" -ForegroundColor Green
Write-Host "Starting log monitoring..." -ForegroundColor Yellow

# Clear existing logs
Write-Host "Clearing existing logs..." -ForegroundColor Cyan
adb logcat -c

# Start monitoring with color-coded output
Write-Host "Monitoring SecureCam logs..." -ForegroundColor Green
Write-Host "Press Ctrl+C to stop monitoring" -ForegroundColor Red
Write-Host ""

# Monitor logs with tag filtering
adb logcat -s MainActivity:V ForegroundService:V Camera2Video:V SimpleHttpServer:V NetworkUtils:V CameraSettings:V
