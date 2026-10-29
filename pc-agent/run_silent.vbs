Set fso = CreateObject("Scripting.FileSystemObject")
fullPath = fso.GetAbsolutePathName(WScript.ScriptFullName)
scriptDir = fso.GetParentFolderName(fullPath)
Set WshShell = CreateObject("WScript.Shell")
WshShell.CurrentDirectory = scriptDir

' 1. Cleanly stop any stale process on port 5050 and InputHelper.exe
WshShell.Run "cmd.exe /c stop_server.bat", 0, True
WScript.Sleep 500

' 2. Launch server.js completely invisible using system node in working directory
WshShell.Run "cmd.exe /c node server.js", 0, False
