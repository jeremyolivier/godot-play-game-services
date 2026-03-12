if not defined KIZZ_DIR (echo ERROR: KIZZ_DIR is not defined & exit /b 1)
robocopy "GodotPlayGameServices" "%KIZZ_DIR%\addons\GodotPlayGameServices" /E /R:0 /W:0