Set fso = CreateObject("Scripting.FileSystemObject")
fullPath = fso.GetAbsolutePathName(WScript.ScriptFullName)
scriptDir = fso.GetParentFolderName(fullPath)
Set WshShell = CreateObject("WScript.Shell")
WshShell.CurrentDirectory = scriptDir
WshShell.Run """C:\Program Files\nodejs\node.exe"" server.js", 0, False
