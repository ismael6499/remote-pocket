Set fso = CreateObject("Scripting.FileSystemObject")
fullPath = fso.GetAbsolutePathName(WScript.ScriptFullName)
scriptDir = fso.GetParentFolderName(fullPath)
Set WshShell = CreateObject("WScript.Shell")
WshShell.CurrentDirectory = scriptDir

nodeExe = "node.exe"
If fso.FileExists("C:\Program Files\nodejs\node.exe") Then
    nodeExe = """C:\Program Files\nodejs\node.exe"""
End If

WshShell.Run nodeExe & " """ & scriptDir & "\server.js""", 0, False
