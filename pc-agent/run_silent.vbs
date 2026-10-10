Set fso = CreateObject("Scripting.FileSystemObject")
fullPath = fso.GetAbsolutePathName(WScript.ScriptFullName)
scriptDir = fso.GetParentFolderName(fullPath)
Set WshShell = CreateObject("WScript.Shell")
WshShell.CurrentDirectory = scriptDir
WshShell.Run "cmd.exe /c """ & Chr(34) & "C:\Program Files\nodejs\node.exe" & Chr(34) & " server.js > log.txt 2>&1""", 0, False




