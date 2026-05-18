@echo off
cd /d e:\Github\DebtTracker

REM Create base structure
mkdir app\src\main\java\com\example\debt_tracker\entity 2>nul
mkdir app\src\main\java\com\example\debt_tracker\dao 2>nul
mkdir app\src\main\java\com\example\debt_tracker\database 2>nul
mkdir app\src\main\java\com\example\debt_tracker\repository 2>nul
mkdir app\src\main\java\com\example\debt_tracker\service 2>nul
mkdir app\src\main\java\com\example\debt_tracker\validation 2>nul
mkdir app\src\main\java\com\example\debt_tracker\controller 2>nul

REM Create view subdirectories
mkdir app\src\main\java\com\example\debt_tracker\view\activity 2>nul
mkdir app\src\main\java\com\example\debt_tracker\view\screen 2>nul
mkdir app\src\main\java\com\example\debt_tracker\view\adapter 2>nul
mkdir app\src\main\java\com\example\debt_tracker\view\dialog 2>nul
mkdir app\src\main\java\com\example\debt_tracker\view\component 2>nul

REM Create shared subdirectories
mkdir app\src\main\java\com\example\debt_tracker\shared\core 2>nul
mkdir app\src\main\java\com\example\debt_tracker\shared\util 2>nul
mkdir app\src\main\java\com\example\debt_tracker\shared\ui 2>nul

REM Create placeholder files
type nul > app\src\main\java\com\example\debt_tracker\entity\.placeholder
type nul > app\src\main\java\com\example\debt_tracker\dao\.placeholder
type nul > app\src\main\java\com\example\debt_tracker\database\.placeholder
type nul > app\src\main\java\com\example\debt_tracker\repository\.placeholder
type nul > app\src\main\java\com\example\debt_tracker\service\.placeholder
type nul > app\src\main\java\com\example\debt_tracker\validation\.placeholder
type nul > app\src\main\java\com\example\debt_tracker\controller\.placeholder
type nul > app\src\main\java\com\example\debt_tracker\view\activity\.placeholder
type nul > app\src\main\java\com\example\debt_tracker\view\screen\.placeholder
type nul > app\src\main\java\com\example\debt_tracker\view\adapter\.placeholder
type nul > app\src\main\java\com\example\debt_tracker\view\dialog\.placeholder
type nul > app\src\main\java\com\example\debt_tracker\view\component\.placeholder
type nul > app\src\main\java\com\example\debt_tracker\shared\core\.placeholder
type nul > app\src\main\java\com\example\debt_tracker\shared\util\.placeholder
type nul > app\src\main\java\com\example\debt_tracker\shared\ui\.placeholder

echo.
echo ✓ Directory structure created successfully!
echo.
pause
