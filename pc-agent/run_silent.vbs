Set fso = CreateObject("Scripting.FileSystemObject")
fullPath = fso.GetAbsolutePathName(WScript.ScriptFullName)
scriptDir = fso.GetParentFolderName(fullPath)
Set WshShell = CreateObject("WScript.Shell")
WshShell.CurrentDirectory = scriptDir

' 1. Cleanly stop any stale process on port 5050 and InputHelper.exe
WshShell.Run Chr(34) & scriptDir & "\stop_server.bat" & Chr(34), 0, True
WScript.Sleep 1000

' 2. Launch start_server.bat completely invisible
WshShell.Run "cmd.exe /c " & Chr(34) & scriptDir & "\start_server.bat" & Chr(34), 0, False
