Set fso = CreateObject("Scripting.FileSystemObject")
fullPath = fso.GetAbsolutePathName(WScript.ScriptFullName)
scriptDir = fso.GetParentFolderName(fullPath)
Set WshShell = CreateObject("WScript.Shell")
WshShell.CurrentDirectory = scriptDir

' 1. Cleanly stop any stale process on port 5050 and InputHelper.exe
WshShell.Run "cmd.exe /c """ & scriptDir & "\stop_server.bat""", 0, True
WScript.Sleep 1500

nodeExe = "node.exe"
If fso.FileExists("C:\Program Files\nodejs\node.exe") Then
    nodeExe = """C:\Program Files\nodejs\node.exe"""
End If

WshShell.Run nodeExe & " """ & scriptDir & "\server.js""", 0, False
