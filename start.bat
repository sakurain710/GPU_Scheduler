@echo off
chcp 936 >nul  :: 强制使用 Windows 默认的 GBK (ANSI) 编码
title SpringBoot 项目一键启动停止脚本（适配gpu-scheduler）

:: ======================== 基础配置 ========================
:: Redis 环境变量
set "REDIS_HOST=192.168.134.128"
set "REDIS_PORT=6379"
set "REDIS_PASSWORD="
set "REDIS_DB=0"

:: MySQL 环境变量（已使用双引号包裹以安全处理 & 符号）
set "DB_URL=jdbc:mysql://192.168.134.128:3306/rbac_test_db?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai"
set "DB_USERNAME=lin"
set "DB_PASSWORD=N3w$tr0ngP@ss!"

:: JWT 环境变量
set "JWT_SECRET_KEY=your-256-bit-secret-key-for-jwt-signing-must-be-at-least-32-characters-long"
set "JWT_ACCESS_TOKEN_EXPIRATION=86400000"
set "JWT_REFRESH_TOKEN_EXPIRATION=604800000"
set "JWT_ISSUER=gpu-scheduler"

:: 项目启动命令 (二选一)
set "START_CMD=mvn spring-boot:run"
:: set "START_CMD=java -jar target/你的项目名称.jar"

:: ======================== 核心功能 ========================
:MENU
cls
echo.
echo ============================== 项目启动停止脚本 ==============================
echo  1. 启动项目（自动配置环境变量+清理缓存）
echo  2. 优雅停止项目（推荐，避免进程残留）
echo  3. 强制停止所有相关进程（窗口卡死时用）
echo  4. 退出脚本
echo =============================================================================
echo.
set /p CHOICE=请输入操作编号（1-4）：

if "%CHOICE%"=="1" goto START_PROJECT
if "%CHOICE%"=="2" goto STOP_PROJECT
if "%CHOICE%"=="3" goto FORCE_STOP
if "%CHOICE%"=="4" goto EXIT_SCRIPT

echo.
echo 错误：请输入正确的编号（1-4）！
pause
goto MENU

:START_PROJECT
echo.
echo ============================== 准备启动项目 ==============================
echo 1. 正在清理项目缓存...
if exist .m2\repository\com\yourpackage (
    rd /s /q .m2\repository\com\yourpackage
)
echo 2. 正在配置环境变量...
echo 3. 正在启动项目（按 Ctrl+C 可停止）...
echo =============================================================================
echo.
%START_CMD%
pause
goto MENU

:STOP_PROJECT
echo.
echo ============================== 优雅停止项目 ==============================
echo 正在发送停止信号，请稍候...
echo （若提示"终止批处理操作吗(Y/N)？"，请输入 Y 并回车）
echo =============================================================================
taskkill /f /im cmd.exe /fi "WINDOWTITLE eq SpringBoot 项目一键启动停止脚本（适配gpu-scheduler）"
echo 项目已优雅停止！
pause
goto MENU

:FORCE_STOP
echo.
echo ============================== 强制停止项目 ==============================
echo 正在强制终止所有相关进程（Maven/Java）...
taskkill /f /im mvn.exe >nul 2>&1
taskkill /f /im java.exe >nul 2>&1
echo 所有相关进程已强制终止！
pause
goto MENU

:EXIT_SCRIPT
echo.
echo 正在退出脚本...
exit /b