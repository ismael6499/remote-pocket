WScript.Sleep 3000
Set fso = CreateObject("Scripting.FileSystemObject")
fullPath = fso.GetAbsolutePathName(WScript.ScriptFullName)
scriptDir = fso.GetParentFolderName(fullPath)
Set WshShell = CreateObject("WScript.Shell")
WshShell.CurrentDirectory = scriptDir
WshShell.Run "cmd.exe /c " & Chr(34) & scriptDir & "\start_server.bat" & Chr(34), 0, False

