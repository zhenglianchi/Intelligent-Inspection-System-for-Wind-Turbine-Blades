@echo off
chcp 65001 >nul
title 风电场智能监测系统

echo 正在启动服务...
echo.
echo 1/4 启动 auth-service (8082) ...
start "auth-service" cmd /c "java -jar auth-service\target\auth-service-1.0.0-SNAPSHOT.jar"

echo 等待 auth-service 就绪...
timeout /t 10 /nobreak >nul

echo 2/4 启动 realtime-service (8083) ...
start "realtime-service" cmd /c "java -jar realtime-service\target\realtime-service-1.0.0-SNAPSHOT.jar"

echo 等待 realtime-service 就绪...
timeout /t 10 /nobreak >nul

echo 3/4 启动 agent-service (8084) ...
start "agent-service" cmd /c "java -jar agent-service\target\agent-service-1.0.0-SNAPSHOT.jar"

echo 等待 agent-service 就绪...
timeout /t 10 /nobreak >nul

echo 4/4 启动 api-gateway (8080) ...
start "api-gateway" cmd /c "java -jar api-gateway\target\api-gateway-1.0.0-SNAPSHOT.jar"

echo.
echo ============================================
echo   全部启动完成!
echo.
echo   前端: http://localhost:5173 (需单独 npm run dev)
echo   Nacos: http://localhost:8848/nacos
echo.
echo   关闭本窗口不会停止服务，各服务在独立窗口中运行
echo ============================================
echo.
pause
