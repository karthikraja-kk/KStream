@echo off
set CMD_LINE_ARGS=%*
java -jar "%~dp0gradle\wrapper\gradle-wrapper.jar" %CMD_LINE_ARGS%
