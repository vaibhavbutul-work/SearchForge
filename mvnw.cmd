@echo off
pushd "%~dp0backend"
call ".\mvnw.cmd" %*
set MVN_EXIT_CODE=%ERRORLEVEL%
popd
exit /b %MVN_EXIT_CODE%
