@echo off
chcp 936 >nul  :: ǿ��ʹ�� Windows Ĭ�ϵ� GBK (ANSI) ����
title SpringBoot ��Ŀһ������ֹͣ�ű�������gpu-scheduler��

:: ======================== �������� ========================
:: Redis ��������
set "REDIS_HOST=192.168.134.128"
set "REDIS_PORT=6379"
set "REDIS_PASSWORD="
set "REDIS_DB=0"

:: MySQL ������������ʹ��˫���Ű����԰�ȫ���� & ���ţ�
set "DB_URL=jdbc:mysql://192.168.134.128:3306/gpu_scheduler_db?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai"
set "DB_USERNAME=lin"
set "DB_PASSWORD=N3w$tr0ngP@ss!"

:: JWT ��������
set "JWT_SECRET_KEY=your-256-bit-secret-key-for-jwt-signing-must-be-at-least-32-characters-long"
set "JWT_ACCESS_TOKEN_EXPIRATION=86400000"
set "JWT_REFRESH_TOKEN_EXPIRATION=604800000"
set "JWT_ISSUER=gpu-scheduler"

:: ��Ŀ�������� (��ѡһ)
set "START_CMD=mvn spring-boot:run"
:: set "START_CMD=java -jar target/�����Ŀ����.jar"

:: ======================== ���Ĺ��� ========================
:MENU
cls
echo.
echo ============================== ��Ŀ����ֹͣ�ű� ==============================
echo  1. ������Ŀ���Զ����û�������+�����棩
echo  2. ����ֹͣ��Ŀ���Ƽ���������̲�����
echo  3. ǿ��ֹͣ������ؽ��̣����ڿ���ʱ�ã�
echo  4. �˳��ű�
echo =============================================================================
echo.
set /p CHOICE=�����������ţ�1-4����

if "%CHOICE%"=="1" goto START_PROJECT
if "%CHOICE%"=="2" goto STOP_PROJECT
if "%CHOICE%"=="3" goto FORCE_STOP
if "%CHOICE%"=="4" goto EXIT_SCRIPT

echo.
echo ������������ȷ�ı�ţ�1-4����
pause
goto MENU

:START_PROJECT
echo.
echo ============================== ׼��������Ŀ ==============================
echo 1. ����������Ŀ����...
if exist .m2\repository\com\yourpackage (
    rd /s /q .m2\repository\com\yourpackage
)
echo 2. �������û�������...
echo 3. ����������Ŀ���� Ctrl+C ��ֹͣ��...
echo =============================================================================
echo.
%START_CMD%
pause
goto MENU

:STOP_PROJECT
echo.
echo ============================== ����ֹͣ��Ŀ ==============================
echo ���ڷ���ֹͣ�źţ����Ժ�...
echo ������ʾ"��ֹ�����������(Y/N)��"�������� Y ���س���
echo =============================================================================
taskkill /f /im cmd.exe /fi "WINDOWTITLE eq SpringBoot ��Ŀһ������ֹͣ�ű�������gpu-scheduler��"
echo ��Ŀ������ֹͣ��
pause
goto MENU

:FORCE_STOP
echo.
echo ============================== ǿ��ֹͣ��Ŀ ==============================
echo ����ǿ����ֹ������ؽ��̣�Maven/Java��...
taskkill /f /im mvn.exe >nul 2>&1
taskkill /f /im java.exe >nul 2>&1
echo ������ؽ�����ǿ����ֹ��
pause
goto MENU

:EXIT_SCRIPT
echo.
echo �����˳��ű�...
exit /b