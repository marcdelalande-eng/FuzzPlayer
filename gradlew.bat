@echo off
set VER=9.6.0
set BASE=%USERPROFILE%\.gradle\manual-wrapper\gradle-%VER%
set BIN=%BASE%\gradle-%VER%\bin\gradle.bat
if not exist "%BIN%" (
  if not exist "%BASE%" mkdir "%BASE%"
  powershell -NoProfile -ExecutionPolicy Bypass -Command "$u='https://services.gradle.org/distributions/gradle-%VER%-bin.zip'; $z='%BASE%\gradle-%VER%-bin.zip'; Invoke-WebRequest -Uri $u -OutFile $z; Expand-Archive -Force $z '%BASE%'"
)
call "%BIN%" %*
