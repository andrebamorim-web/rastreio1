@echo off
chcp 65001 >nul
setlocal
REM ==========================================================================
REM  Instala o app de rastreio no celular e o torna "Device Owner".
REM  Coloque este arquivo, o adb.exe (Platform Tools) e o rastreio.apk
REM  na mesma pasta. Celular conectado no USB com Depuração USB ativa.
REM ==========================================================================

set PKG=br.com.hasolucoes.rastreio

echo.
echo [1/6] Procurando o celular...
adb devices
adb get-state >nul 2>&1 || (echo ERRO: celular nao encontrado. Confira o cabo e aceite o aviso de depuracao no celular. & pause & exit /b 1)

echo.
echo [2/6] Instalando o app...
adb install -r rastreio.apk || (echo ERRO na instalacao. & pause & exit /b 1)

echo.
echo [3/6] Ligando localizacao (alta precisao) e dados moveis...
adb shell settings put secure location_providers_allowed +gps
adb shell settings put secure location_providers_allowed +network
adb shell svc data enable

echo.
echo [4/6] Retirando o app da economia de bateria...
adb shell dumpsys deviceidle whitelist +%PKG%

echo.
echo [5/6] Conferindo contas no aparelho...
adb shell dumpsys account | findstr /C:"Account {"
echo  (Se aparecer alguma conta acima, o proximo passo vai falhar.
echo   Remova as contas em Configuracoes ^> Contas, ou faca o reset de fabrica.)
pause

echo.
echo [6/6] Tornando o app administrador do aparelho...
adb shell dpm set-device-owner %PKG%/.AdminReceiver || (echo ERRO: veja a mensagem acima. & pause & exit /b 1)

echo.
echo ===================================================================
echo  Pronto. A depuracao USB sera desligada pelo app em seguida.
echo  Agora adicione a conta Google e configure o WhatsApp normalmente.
echo  Painel do administrador: disque *#*#7278#*#* no Telefone.
echo ===================================================================
pause
