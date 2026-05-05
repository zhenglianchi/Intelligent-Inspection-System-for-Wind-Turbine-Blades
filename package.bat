@echo off
chcp 65001 >nul
echo ============================================
echo   风电场智能监测系统 - 一键打包
echo ============================================
echo.

call mvn clean package -DskipTests -q

echo.
echo 打包完成。JAR 包位置:
echo   auth-service\target\auth-service-1.0.0-SNAPSHOT.jar
echo   realtime-service\target\realtime-service-1.0.0-SNAPSHOT.jar
echo   agent-service\target\agent-service-1.0.0-SNAPSHOT.jar
echo   api-gateway\target\api-gateway-1.0.0-SNAPSHOT.jar
echo.
pause
