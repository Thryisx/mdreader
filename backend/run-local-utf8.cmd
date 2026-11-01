@echo off
REM 后端本地启动：先把 Windows 控制台切到 UTF-8，再启动 Spring Boot，避免中文日志乱码。
chcp 65001 >nul
set JAVA_TOOL_OPTIONS=-Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8
cd /d %~dp0
mvn spring-boot:run
