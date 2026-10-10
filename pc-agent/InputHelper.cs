using System;
using System.Collections.Generic;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.Drawing.Imaging;
using System.IO;
using System.Runtime.InteropServices;
using System.Text;
using System.Threading;
using System.Windows.Forms;

namespace InputHelper {
    [Guid("5CDF2C82-841E-4546-9722-0CF74078229A"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    internal interface IAudioEndpointVolume {
        [PreserveSig] int RegisterControlChangeNotify(IntPtr pNotify);
        [PreserveSig] int UnregisterControlChangeNotify(IntPtr pNotify);
        [PreserveSig] int GetChannelCount(out uint pnChannelCount);
        [PreserveSig] int SetMasterVolumeLevel(float fLevelDB, ref Guid pguidEventContext);
        [PreserveSig] int SetMasterVolumeLevelScalar(float fLevel, ref Guid pguidEventContext);
        [PreserveSig] int GetMasterVolumeLevel(out float pfLevelDB);
        [PreserveSig] int GetMasterVolumeLevelScalar(out float pfLevel);
        [PreserveSig] int SetChannelVolumeLevel(uint nChannel, float fLevelDB, ref Guid pguidEventContext);
        [PreserveSig] int SetChannelVolumeLevelScalar(uint nChannel, float fLevel, ref Guid pguidEventContext);
        [PreserveSig] int GetChannelVolumeLevel(uint nChannel, out float pfLevelDB);
        [PreserveSig] int GetChannelVolumeLevelScalar(uint nChannel, out float pfLevel);
        [PreserveSig] int SetMute([MarshalAs(UnmanagedType.Bool)] bool bMute, ref Guid pguidEventContext);
        [PreserveSig] int GetMute([MarshalAs(UnmanagedType.Bool)] out bool pbMute);
        [PreserveSig] int GetVolumeStepInfo(out uint pnStep, out uint pnStepCount);
        [PreserveSig] int VolumeStepUp(ref Guid pguidEventContext);
        [PreserveSig] int VolumeStepDown(ref Guid pguidEventContext);
        [PreserveSig] int QueryHardwareSupport(out uint pdwHardwareSupport);
    }

    [Guid("C02216F6-8C67-4B5B-9786-D5C4DF585117"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    internal interface IAudioMeterInformation {
        [PreserveSig] int GetPeakValue(out float pfPeak);
    }

    [Guid("D666063F-1587-4E43-81F1-B948E807363F"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    internal interface IMMDevice {
        [PreserveSig] int Activate(ref Guid id, uint clsCtx, IntPtr activationParams, out IntPtr ppInterface);
    }

    [Guid("A95664D2-9614-4F35-A746-DE8DB63617E6"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    internal interface IMMDeviceEnumerator {
        [PreserveSig] int EnumAudioEndpoints(int dataFlow, uint stateMask, out IntPtr ppDevices);
        [PreserveSig] int GetDefaultAudioEndpoint(int dataFlow, int role, out IMMDevice endpoint);
    }

    [ComImport, Guid("BCDE0395-E52F-467C-8E3D-C4579291692E")]
    internal class MMDeviceEnumeratorComObject { }

    public class Program {
        [DllImport("user32.dll")]
        private static extern bool SetProcessDPIAware();

        [DllImport("user32.dll")]
        private static extern void mouse_event(uint dwFlags, int dx, int dy, uint dwData, UIntPtr dwExtraInfo);

        [DllImport("user32.dll")]
        private static extern void keybd_event(byte bVk, byte bScan, uint dwFlags, UIntPtr dwExtraInfo);

        [DllImport("user32.dll")]
        private static extern uint MapVirtualKey(uint uCode, uint uMapType);

        [DllImport("user32.dll")]
        private static extern bool GetCursorPos(out POINT lpPoint);

        [DllImport("user32.dll")]
        private static extern bool SetCursorPos(int X, int Y);

        [DllImport("user32.dll")]
        private static extern int GetSystemMetrics(int nIndex);

        [StructLayout(LayoutKind.Sequential)]
        private struct CURSORINFO {
            public int cbSize;
            public int flags;
            public IntPtr hCursor;
            public POINT ptScreenPos;
        }

        [DllImport("user32.dll")]
        private static extern bool GetCursorInfo(out CURSORINFO pci);

        [DllImport("user32.dll")]
        private static extern bool DrawIcon(IntPtr hDC, int X, int Y, IntPtr hIcon);

        [DllImport("user32.dll", SetLastError = true)]
        private static extern bool DrawIconEx(IntPtr hdc, int xLeft, int yTop, IntPtr hIcon, int cxWidth, int cyWidth, uint istepIfAniCur, IntPtr hbrFlickerFreeDraw, uint diFlags);

        [DllImport("user32.dll", SetLastError = true)]
        private static extern IntPtr OpenWindowStation(string lpszWinSta, bool fInherit, uint dwDesiredAccess);

        [DllImport("user32.dll", SetLastError = true)]
        private static extern bool SetProcessWindowStation(IntPtr hWinSta);

        [DllImport("user32.dll", SetLastError = true)]
        private static extern IntPtr OpenDesktop(string lpszDesktop, uint dwFlags, bool fInherit, uint dwDesiredAccess);

        [DllImport("user32.dll", SetLastError = true)]
        private static extern bool SetThreadDesktop(IntPtr hDesktop);

        [DllImport("user32.dll", SetLastError = true)]
        private static extern IntPtr GetDC(IntPtr hWnd);

        [DllImport("user32.dll", SetLastError = true)]
        private static extern int ReleaseDC(IntPtr hWnd, IntPtr hDC);

        [DllImport("gdi32.dll")]
        private static extern IntPtr CreateDC(string lpszDriver, string lpszDevice, string lpszOutput, IntPtr lpInitData);

        [DllImport("gdi32.dll")]
        private static extern bool DeleteDC(IntPtr hdc);

        [DllImport("gdi32.dll", SetLastError = true)]
        private static extern bool BitBlt(IntPtr hdcDest, int nXDest, int nYDest, int nWidth, int nHeight, IntPtr hdcSrc, int nXSrc, int nYSrc, uint dwRop);

        private const uint SRCCOPY = 0x00CC0020;
        private const uint CAPTUREBLT = 0x40000000;

        private const int CURSOR_SHOWING = 0x00000001;

        private const uint MOUSEEVENTF_MOVE = 0x0001;
        private const uint MOUSEEVENTF_LEFTDOWN = 0x0002;
        private const uint MOUSEEVENTF_LEFTUP = 0x0004;
        private const uint MOUSEEVENTF_RIGHTDOWN = 0x0008;
        private const uint MOUSEEVENTF_RIGHTUP = 0x0010;
        private const uint MOUSEEVENTF_MIDDLEDOWN = 0x0020;
        private const uint MOUSEEVENTF_MIDDLEUP = 0x0040;
        private const uint MOUSEEVENTF_WHEEL = 0x0800;

        [StructLayout(LayoutKind.Sequential)]
        private struct MOUSEINPUT {
            public int dx;
            public int dy;
            public uint mouseData;
            public uint dwFlags;
            public uint time;
            public IntPtr dwExtraInfo;
        }

        [StructLayout(LayoutKind.Sequential)]
        private struct KEYBDINPUT {
            public ushort wVk;
            public ushort wScan;
            public uint dwFlags;
            public uint time;
            public IntPtr dwExtraInfo;
        }

        [StructLayout(LayoutKind.Sequential)]
        private struct HARDWAREINPUT {
            public uint uMsg;
            public ushort wParamL;
            public ushort wParamH;
        }

        [StructLayout(LayoutKind.Explicit)]
        private struct INPUTDATA {
            [FieldOffset(0)] public MOUSEINPUT mi;
            [FieldOffset(0)] public KEYBDINPUT ki;
            [FieldOffset(0)] public HARDWAREINPUT hi;
        }

        [StructLayout(LayoutKind.Sequential)]
        private struct INPUT {
            public uint type;
            public INPUTDATA data;
        }

        private const uint INPUT_MOUSE = 0;
        private const uint INPUT_KEYBOARD = 1;
        private const uint KEYEVENTF_EXTENDEDKEY = 0x0001;
        private const uint KEYEVENTF_KEYUP = 0x0002;
        private const uint KEYEVENTF_UNICODE = 0x0004;

        [DllImport("user32.dll", SetLastError = true)]
        private static extern uint SendInput(uint nInputs, INPUT[] pInputs, int cbSize);

        [StructLayout(LayoutKind.Sequential)]
        private struct POINT {
            public int X;
            public int Y;
        }

        private static System.Threading.Timer watchdogTimer = null;
        private static readonly object watchdogLock = new object();

        private static void ArmWatchdog(int ms = 450) {
            lock (watchdogLock) {
                if (watchdogTimer == null) {
                    watchdogTimer = new System.Threading.Timer((s) => {
                        try { ReleaseAllModifiers(); } catch { }
                    }, null, ms, Timeout.Infinite);
                } else {
                    watchdogTimer.Change(ms, Timeout.Infinite);
                }
            }
        }

        private static bool IsExtendedKey(byte vk) {
            return vk == 0x5B || vk == 0x5C || vk == 0xA3 || vk == 0xA5 ||
                   vk == 0x2D || vk == 0x2E || vk == 0x24 || vk == 0x23 ||
                   vk == 0x21 || vk == 0x22 || (vk >= 0x25 && vk <= 0x28) ||
                   (vk >= 0xAD && vk <= 0xB3);
        }

        private static void SendKeyDown(byte vk) {
            uint scan = MapVirtualKey((uint)vk, 0);
            uint flags = 0;
            if (IsExtendedKey(vk)) flags |= KEYEVENTF_EXTENDEDKEY;

            INPUT[] inputs = new INPUT[1];
            inputs[0] = new INPUT {
                type = INPUT_KEYBOARD,
                data = new INPUTDATA {
                    ki = new KEYBDINPUT {
                        wVk = vk,
                        wScan = (ushort)scan,
                        dwFlags = flags,
                        time = 0,
                        dwExtraInfo = IntPtr.Zero
                    }
                }
            };
            uint sent = SendInput(1, inputs, Marshal.SizeOf(typeof(INPUT)));
            if (sent == 0) {
                keybd_event(vk, (byte)scan, flags, UIntPtr.Zero);
            }
        }

        private static void SendKeyUp(byte vk) {
            uint scan = MapVirtualKey((uint)vk, 0);
            uint flags = KEYEVENTF_KEYUP;
            if (IsExtendedKey(vk)) flags |= KEYEVENTF_EXTENDEDKEY;

            INPUT[] inputs = new INPUT[1];
            inputs[0] = new INPUT {
                type = INPUT_KEYBOARD,
                data = new INPUTDATA {
                    ki = new KEYBDINPUT {
                        wVk = vk,
                        wScan = (ushort)scan,
                        dwFlags = flags,
                        time = 0,
                        dwExtraInfo = IntPtr.Zero
                    }
                }
            };
            uint sent = SendInput(1, inputs, Marshal.SizeOf(typeof(INPUT)));
            if (sent == 0) {
                keybd_event(vk, (byte)scan, flags, UIntPtr.Zero);
            }
        }

        public static void ReleaseAllModifiers() {
            byte[] modKeys = new byte[] {
                0x11, 0xA2, 0xA3,
                0x12, 0xA4, 0xA5,
                0x10, 0xA0, 0xA1,
                0x5B, 0x5C
            };
            foreach (byte vk in modKeys) {
                SendKeyUp(vk);
            }
        }

        public static void Main(string[] args) {
            try { SetProcessDPIAware(); } catch { }
            try { EnsureDesktopAccess(); } catch { }
            Console.OutputEncoding = Encoding.UTF8;
            Console.InputEncoding = Encoding.UTF8;

            if (args.Length > 0 && args[0] == "--pipe") {
                RunPipeLoop();
                return;
            }

            if (args.Length > 0 && args[0] == "--stream") {
                int fps = 2;
                int quality = 65;
                int height = 720;
                if (args.Length > 1) int.TryParse(args[1], out fps);
                if (args.Length > 2) int.TryParse(args[2], out quality);
                if (args.Length > 3) int.TryParse(args[3], out height);
                RunStreamLoop(fps, quality, height);
                return;
            }

            if (args.Length > 0 && args[0] == "--capture") {
                int quality = 65;
                int height = 720;
                if (args.Length > 1) int.TryParse(args[1], out quality);
                if (args.Length > 2) int.TryParse(args[2], out height);
                int cx, cy, sw, sh;
                byte[] jpeg = CaptureScreenJpeg(quality, height, out cx, out cy, out sw, out sh);
                Console.WriteLine("FRAME:" + cx + "," + cy + "," + sw + "," + sh + ":" + Convert.ToBase64String(jpeg));
                return;
            }

            if (args.Length > 0) {
                ExecuteCommand(string.Join(" ", args));
                return;
            }

            Console.WriteLine("Usage: InputHelper.exe --pipe | --stream [fps] [quality] [height] | --capture | <command>");
        }

        private static void RunPipeLoop() {
            try { EnsureDesktopAccess(); } catch { }
            Console.WriteLine("READY");
            string line;
            while ((line = Console.ReadLine()) != null) {
                line = line.Trim();
                if (string.IsNullOrEmpty(line)) continue;
                if (line.Equals("exit", StringComparison.OrdinalIgnoreCase) || line.Equals("quit", StringComparison.OrdinalIgnoreCase)) {
                    break;
                }
                if (line.Equals("ping", StringComparison.OrdinalIgnoreCase)) {
                    Console.WriteLine("PONG");
                    continue;
                }

                try {
                    ExecuteCommand(line);
                } catch (Exception ex) {
                    Console.WriteLine("ERR " + ex.Message);
                }
            }
        }

        private static void RunStreamLoop(int fps, int quality, int targetHeight) {
            if (fps <= 0) fps = 2;
            int intervalMs = 1000 / fps;
            Console.WriteLine("STREAM_READY");

            bool running = true;
            Thread inputThread = new Thread(() => {
                try {
                    string line;
                    while ((line = Console.ReadLine()) != null) {
                        line = line.Trim();
                        if (line.Equals("stop", StringComparison.OrdinalIgnoreCase) ||
                            line.Equals("exit", StringComparison.OrdinalIgnoreCase) ||
                            line.Equals("quit", StringComparison.OrdinalIgnoreCase)) {
                            running = false;
                            break;
                        }
                    }
                } catch { }
                running = false;
            });
            inputThread.IsBackground = true;
            inputThread.Start();

            while (running) {
                long start = Environment.TickCount;
                try {
                    int cx, cy, sw, sh;
                    byte[] jpeg = CaptureScreenJpeg(quality, targetHeight, out cx, out cy, out sw, out sh);
                    Console.WriteLine("FRAME:" + cx + "," + cy + "," + sw + "," + sh + ":" + Convert.ToBase64String(jpeg));
                } catch (Exception ex) {
                    Console.WriteLine("ERR " + ex.Message);
                }
                long elapsed = Environment.TickCount - start;
                int sleepMs = (int)(intervalMs - elapsed);
                if (sleepMs > 0) {
                    Thread.Sleep(sleepMs);
                }
            }
        }

        private static void ExecuteCommand(string cmdLine) {
            if (string.IsNullOrEmpty(cmdLine)) return;
            string[] parts = cmdLine.Split(new char[] { ' ' }, 2);
            string action = parts[0].ToLowerInvariant();
            string rest = parts.Length > 1 ? parts[1].Trim() : "";

            switch (action) {
                case "m":
                case "move":
                    MouseMove(rest);
                    Console.WriteLine("OK");
                    break;
                case "c":
                case "click":
                    MouseClick(rest);
                    Console.WriteLine("OK");
                    break;
                case "dc":
                case "doubleclick":
                case "double-click":
                    MouseDoubleClick(rest);
                    Console.WriteLine("OK");
                    break;
                case "d":
                case "down":
                    MouseDown(rest);
                    Console.WriteLine("OK");
                    break;
                case "u":
                case "up":
                    MouseUp(rest);
                    Console.WriteLine("OK");
                    break;
                case "s":
                case "scroll":
                    MouseScroll(rest);
                    Console.WriteLine("OK");
                    break;
                case "t":
                case "type":
                case "text":
                    TypeText(rest);
                    Console.WriteLine("OK");
                    break;
                case "k":
                case "key":
                    PressKey(rest);
                    Console.WriteLine("OK");
                    break;
                case "p":
                case "paste":
                    string pasteText = "";
                    try {
                        byte[] bytes = Convert.FromBase64String(rest);
                        pasteText = Encoding.UTF8.GetString(bytes);
                    } catch {
                        pasteText = rest;
                    }
                    SetClipboardAndPaste(pasteText);
                    Console.WriteLine("OK");
                    break;
                case "delline":
                case "line_delete":
                case "deleteline":
                    DeleteLine();
                    Console.WriteLine("OK");
                    break;
                case "r":
                case "reset":
                case "release":
                    ReleaseAllModifiers();
                    mouse_event(MOUSEEVENTF_LEFTUP, 0, 0, 0, UIntPtr.Zero);
                    mouse_event(MOUSEEVENTF_RIGHTUP, 0, 0, 0, UIntPtr.Zero);
                    mouse_event(MOUSEEVENTF_MIDDLEUP, 0, 0, 0, UIntPtr.Zero);
                    Console.WriteLine("OK");
                    break;
                case "shot":
                case "capture":
                    int q = 65;
                    int h = 720;
                    if (!string.IsNullOrEmpty(rest)) {
                        string[] p = rest.Split(new char[] { ' ' }, StringSplitOptions.RemoveEmptyEntries);
                        if (p.Length > 0) int.TryParse(p[0], out q);
                        if (p.Length > 1) int.TryParse(p[1], out h);
                    }
                    int cx, cy, sw, sh;
                    byte[] frame = CaptureScreenJpeg(q, h, out cx, out cy, out sw, out sh);
                    Console.WriteLine("FRAME:" + cx + "," + cy + "," + sw + "," + sh + ":" + Convert.ToBase64String(frame));
                    break;
                case "vol":
                case "volume":
                    if (string.IsNullOrEmpty(rest)) {
                        Console.WriteLine("AUDIO:" + GetAudioStatusJson());
                    } else {
                        float v;
                        if (float.TryParse(rest, out v)) {
                            SetAudioVolume(v);
                            Console.WriteLine("OK");
                        } else {
                            Console.WriteLine("ERR Invalid volume number");
                        }
                    }
                    break;
                case "mute":
                    if (string.IsNullOrEmpty(rest)) {
                        ToggleAudioMute();
                    } else {
                        bool m;
                        if (bool.TryParse(rest, out m)) {
                            SetAudioMute(m);
                        } else {
                            ToggleAudioMute();
                        }
                    }
                    Console.WriteLine("OK");
                    break;
                case "clip":
                case "getclip":
                    string clipVal = GetClipboardText();
                    Console.WriteLine("CLIP:" + Convert.ToBase64String(Encoding.UTF8.GetBytes(clipVal ?? "")));
                    break;
                case "setclip":
                    string textToSet = "";
                    try {
                        byte[] bytes = Convert.FromBase64String(rest);
                        textToSet = Encoding.UTF8.GetString(bytes);
                    } catch {
                        textToSet = rest;
                    }
                    SetClipboardText(textToSet);
                    Console.WriteLine("OK");
                    break;
                default:
                    throw new ArgumentException("Unknown action: " + action);
            }
        }

        public static string GetClipboardText() {
            string result = "";
            Thread staThread = new Thread(() => {
                try {
                    if (Clipboard.ContainsText()) {
                        result = Clipboard.GetText();
                    }
                } catch { }
            });
            staThread.SetApartmentState(ApartmentState.STA);
            staThread.Start();
            staThread.Join(600);
            return result ?? "";
        }

        public static void SetClipboardText(string text) {
            Thread staThread = new Thread(() => {
                try {
                    Clipboard.SetText(text ?? "");
                } catch { }
            });
            staThread.SetApartmentState(ApartmentState.STA);
            staThread.Start();
            staThread.Join(600);
        }

        private static void DeleteLine() {
            PressKey("end");
            Thread.Sleep(10);
            PressKey("shift+home");
            Thread.Sleep(10);
            PressKey("backspace");
            Thread.Sleep(10);
            PressKey("backspace");
        }

        private static void SetClipboardAndPaste(string text) {
            if (string.IsNullOrEmpty(text)) return;
            SetClipboardText(text);
            Thread.Sleep(30);
            PressKey("ctrl+v");
        }

        private static void MouseMove(string args) {
            string[] coords = args.Split(new char[] { ' ', ',' }, StringSplitOptions.RemoveEmptyEntries);
            if (coords.Length < 2) return;
            int dx, dy;
            if (int.TryParse(coords[0], out dx) && int.TryParse(coords[1], out dy)) {
                INPUT[] inputs = new INPUT[1];
                inputs[0] = new INPUT {
                    type = INPUT_MOUSE,
                    data = new INPUTDATA {
                        mi = new MOUSEINPUT {
                            dx = dx,
                            dy = dy,
                            mouseData = 0,
                            dwFlags = MOUSEEVENTF_MOVE,
                            time = 0,
                            dwExtraInfo = IntPtr.Zero
                        }
                    }
                };
                uint sent = SendInput(1, inputs, Marshal.SizeOf(typeof(INPUT)));
                if (sent == 0) {
                    mouse_event(MOUSEEVENTF_MOVE, dx, dy, 0, UIntPtr.Zero);
                }
            }
        }

        private static void MouseClick(string btn) {
            MouseDown(btn);
            Thread.Sleep(15);
            MouseUp(btn);
        }

        private static void MouseDoubleClick(string btn) {
            MouseClick(btn);
            Thread.Sleep(80);
            MouseClick(btn);
        }

        private static void MouseDown(string btn) {
            btn = (btn ?? "").ToLowerInvariant();
            uint flag = MOUSEEVENTF_LEFTDOWN;
            if (btn == "right" || btn == "r") flag = MOUSEEVENTF_RIGHTDOWN;
            else if (btn == "middle" || btn == "m") flag = MOUSEEVENTF_MIDDLEDOWN;

            INPUT[] inputs = new INPUT[1];
            inputs[0] = new INPUT {
                type = INPUT_MOUSE,
                data = new INPUTDATA {
                    mi = new MOUSEINPUT {
                        dx = 0,
                        dy = 0,
                        mouseData = 0,
                        dwFlags = flag,
                        time = 0,
                        dwExtraInfo = IntPtr.Zero
                    }
                }
            };
            uint sent = SendInput(1, inputs, Marshal.SizeOf(typeof(INPUT)));
            if (sent == 0) {
                mouse_event(flag, 0, 0, 0, UIntPtr.Zero);
            }
        }

        private static void MouseUp(string btn) {
            btn = (btn ?? "").ToLowerInvariant();
            uint flag = MOUSEEVENTF_LEFTUP;
            if (btn == "right" || btn == "r") flag = MOUSEEVENTF_RIGHTUP;
            else if (btn == "middle" || btn == "m") flag = MOUSEEVENTF_MIDDLEUP;

            INPUT[] inputs = new INPUT[1];
            inputs[0] = new INPUT {
                type = INPUT_MOUSE,
                data = new INPUTDATA {
                    mi = new MOUSEINPUT {
                        dx = 0,
                        dy = 0,
                        mouseData = 0,
                        dwFlags = flag,
                        time = 0,
                        dwExtraInfo = IntPtr.Zero
                    }
                }
            };
            uint sent = SendInput(1, inputs, Marshal.SizeOf(typeof(INPUT)));
            if (sent == 0) {
                mouse_event(flag, 0, 0, 0, UIntPtr.Zero);
            }
        }

        private static void MouseScroll(string delta) {
            int d;
            if (int.TryParse(delta, out d)) {
                INPUT[] inputs = new INPUT[1];
                inputs[0] = new INPUT {
                    type = INPUT_MOUSE,
                    data = new INPUTDATA {
                        mi = new MOUSEINPUT {
                            dx = 0,
                            dy = 0,
                            mouseData = (uint)d,
                            dwFlags = MOUSEEVENTF_WHEEL,
                            time = 0,
                            dwExtraInfo = IntPtr.Zero
                        }
                    }
                };
                uint sent = SendInput(1, inputs, Marshal.SizeOf(typeof(INPUT)));
                if (sent == 0) {
                    mouse_event(MOUSEEVENTF_WHEEL, 0, 0, (uint)d, UIntPtr.Zero);
                }
            }
        }

        private static void TypeText(string text) {
            if (string.IsNullOrEmpty(text)) return;
            int len = text.Length;
            INPUT[] inputs = new INPUT[len * 2];
            int structSize = Marshal.SizeOf(typeof(INPUT));

            for (int i = 0; i < len; i++) {
                char c = text[i];
                // Key down
                inputs[i * 2] = new INPUT {
                    type = INPUT_KEYBOARD,
                    data = new INPUTDATA {
                        ki = new KEYBDINPUT {
                            wVk = 0,
                            wScan = (ushort)c,
                            dwFlags = KEYEVENTF_UNICODE,
                            time = 0,
                            dwExtraInfo = IntPtr.Zero
                        }
                    }
                };
                // Key up
                inputs[i * 2 + 1] = new INPUT {
                    type = INPUT_KEYBOARD,
                    data = new INPUTDATA {
                        ki = new KEYBDINPUT {
                            wVk = 0,
                            wScan = (ushort)c,
                            dwFlags = KEYEVENTF_UNICODE | KEYEVENTF_KEYUP,
                            time = 0,
                            dwExtraInfo = IntPtr.Zero
                        }
                    }
                };
            }

            SendInput((uint)inputs.Length, inputs, structSize);
        }

        private static readonly Dictionary<string, byte> KeyMap = new Dictionary<string, byte>(StringComparer.OrdinalIgnoreCase) {
            { "enter", 0x0D }, { "return", 0x0D },
            { "esc", 0x1B }, { "escape", 0x1B },
            { "backspace", 0x08 }, { "bksp", 0x08 },
            { "tab", 0x09 },
            { "space", 0x20 },
            { "delete", 0x2E }, { "del", 0x2E },
            { "insert", 0x2D }, { "ins", 0x2D },
            { "home", 0x24 },
            { "end", 0x23 },
            { "pageup", 0x21 }, { "pgup", 0x21 }, { "repag", 0x21 },
            { "pagedown", 0x22 }, { "pgdn", 0x22 }, { "avpag", 0x22 },
            { "up", 0x26 }, { "arrowup", 0x26 },
            { "down", 0x28 }, { "arrowdown", 0x28 },
            { "left", 0x25 }, { "arrowleft", 0x25 },
            { "right", 0x27 }, { "arrowright", 0x27 },
            { "win", 0x5B }, { "windows", 0x5B }, { "lwin", 0x5B },
            { "ctrl", 0x11 }, { "control", 0x11 },
            { "alt", 0x12 }, { "menu", 0x12 },
            { "shift", 0x10 },
            { "capslock", 0x14 },
            { "printscreen", 0x2C }, { "prtscn", 0x2C }, { "prntscrn", 0x2C },
            { "scrolllock", 0x91 },
            { "pause", 0x13 },
            { "f1", 0x70 }, { "f2", 0x71 }, { "f3", 0x72 }, { "f4", 0x73 },
            { "f5", 0x74 }, { "f6", 0x75 }, { "f7", 0x76 }, { "f8", 0x77 },
            { "f9", 0x78 }, { "f10", 0x79 }, { "f11", 0x7A }, { "f12", 0x7B },
            // Punctuation and symbols
            { "-", 0xBD }, { "minus", 0xBD }, { "dash", 0xBD },
            { "=", 0xBB }, { "plus", 0xBB }, { "equal", 0xBB },
            { "[", 0xDB }, { "openbracket", 0xDB }, { "bracketleft", 0xDB },
            { "]", 0xDD }, { "closebracket", 0xDD }, { "bracketright", 0xDD },
            { "\\", 0xDC }, { "backslash", 0xDC },
            { ";", 0xBA }, { "semicolon", 0xBA },
            { "'", 0xDE }, { "quote", 0xDE }, { "singlequote", 0xDE },
            { ",", 0xBC }, { "comma", 0xBC },
            { ".", 0xBE }, { "period", 0xBE }, { "dot", 0xBE },
            { "/", 0xBF }, { "slash", 0xBF },
            { "`", 0xC0 }, { "backquote", 0xC0 }, { "tilde", 0xC0 }, { "grave", 0xC0 },
            // Volume & Media keys
            { "volume_mute", 0xAD }, { "volmute", 0xAD }, { "mute_key", 0xAD },
            { "volume_down", 0xAE }, { "voldown", 0xAE },
            { "volume_up", 0xAF }, { "volup", 0xAF },
            { "media_next", 0xB0 }, { "nexttrack", 0xB0 },
            { "media_prev", 0xB1 }, { "prevtrack", 0xB1 },
            { "media_stop", 0xB2 },
            { "media_play_pause", 0xB3 }, { "playpause", 0xB3 }, { "play_pause", 0xB3 }
        };

        private static void PressKey(string keyExpr) {
            if (string.IsNullOrEmpty(keyExpr)) return;
            if (keyExpr.Equals("delline", StringComparison.OrdinalIgnoreCase) ||
                keyExpr.Equals("delete_line", StringComparison.OrdinalIgnoreCase) ||
                keyExpr.Equals("deleteline", StringComparison.OrdinalIgnoreCase)) {
                DeleteLine();
                return;
            }
            if (keyExpr.Equals("ctrl+alt+del", StringComparison.OrdinalIgnoreCase) ||
                keyExpr.Equals("ctrl+alt+delete", StringComparison.OrdinalIgnoreCase)) {
                try {
                    System.Diagnostics.Process.Start("taskmgr.exe");
                } catch { }
                return;
            }
            if (keyExpr.Equals("win+l", StringComparison.OrdinalIgnoreCase) ||
                keyExpr.Equals("lock", StringComparison.OrdinalIgnoreCase)) {
                try {
                    System.Diagnostics.Process.Start("rundll32.exe", "user32.dll,LockWorkStation");
                } catch { }
                return;
            }
            string[] parts;
            if (keyExpr == "-") {
                parts = new string[] { "-" };
            } else if (keyExpr.EndsWith("+-")) {
                List<string> pList = new List<string>(keyExpr.Substring(0, keyExpr.Length - 2).Split(new char[] { '+' }, StringSplitOptions.RemoveEmptyEntries));
                pList.Add("-");
                parts = pList.ToArray();
            } else {
                parts = keyExpr.Split(new char[] { '+' }, StringSplitOptions.RemoveEmptyEntries);
            }
            List<byte> modifiersToRelease = new List<byte>();
            List<byte> mainKeys = new List<byte>();

            ArmWatchdog(450);

            try {
                for (int i = 0; i < parts.Length; i++) {
                    string p = parts[i].Trim().ToLowerInvariant();
                    if (string.IsNullOrEmpty(p)) continue;

                    if (p == "ctrl" || p == "control") {
                        SendKeyDown(0x11);
                        modifiersToRelease.Add(0x11);
                    } else if (p == "alt") {
                        SendKeyDown(0x12);
                        modifiersToRelease.Add(0x12);
                    } else if (p == "shift") {
                        SendKeyDown(0x10);
                        modifiersToRelease.Add(0x10);
                    } else if (p == "win" || p == "windows" || p == "lwin") {
                        SendKeyDown(0x5B);
                        modifiersToRelease.Add(0x5B);
                    } else {
                        if (KeyMap.ContainsKey(p)) {
                            mainKeys.Add(KeyMap[p]);
                        } else if (p.Length == 1) {
                            char c = p[0];
                            if (c >= 'a' && c <= 'z') mainKeys.Add((byte)('A' + (c - 'a')));
                            else if (c >= '0' && c <= '9') mainKeys.Add((byte)c);
                        }
                    }
                }

                if (mainKeys.Count > 0) {
                    Thread.Sleep(15);
                    foreach (byte k in mainKeys) {
                        SendKeyDown(k);
                    }
                    Thread.Sleep(30);
                    for (int i = mainKeys.Count - 1; i >= 0; i--) {
                        SendKeyUp(mainKeys[i]);
                    }
                    Thread.Sleep(15);
                } else if (modifiersToRelease.Count > 0) {
                    Thread.Sleep(40);
                }
            } finally {
                for (int i = modifiersToRelease.Count - 1; i >= 0; i--) {
                    SendKeyUp(modifiersToRelease[i]);
                }
                ReleaseAllModifiers();
            }
        }

        private static void EnsureDesktopAccess() {
            try {
                IntPtr hwinsta = OpenWindowStation("winsta0", false, 0x000F037F);
                if (hwinsta != IntPtr.Zero) SetProcessWindowStation(hwinsta);
                IntPtr hdesk = OpenDesktop("default", 0, false, 0x000F01FF);
                if (hdesk != IntPtr.Zero) SetThreadDesktop(hdesk);
            } catch { }
        }

        // ==========================================
        // SCREEN CAPTURE (GDI+ with cursor overlay & fallback)
        // ==========================================
        public static byte[] CaptureScreenJpeg(int quality, int targetHeight, out int curX, out int curY, out int scrW, out int scrH) {
            EnsureDesktopAccess();

            int srcW = GetSystemMetrics(0);
            int srcH = GetSystemMetrics(1);
            if (srcW <= 0 || srcH <= 0) {
                srcW = Screen.PrimaryScreen.Bounds.Width;
                srcH = Screen.PrimaryScreen.Bounds.Height;
            }
            if (srcW <= 0) srcW = 1920;
            if (srcH <= 0) srcH = 1080;
            scrW = srcW;
            scrH = srcH;

            // Retrieve live mouse cursor position
            POINT pt;
            if (GetCursorPos(out pt)) {
                curX = pt.X;
                curY = pt.Y;
            } else {
                curX = srcW / 2;
                curY = srcH / 2;
            }

            int destH = targetHeight > 0 ? targetHeight : 720;
            if (destH > srcH) destH = srcH;
            int destW = (int)Math.Round((double)srcW * destH / srcH);
            if (destW % 2 != 0) destW++;

            try {
                IntPtr hSrcDC = GetDC(IntPtr.Zero);
                bool isCreatedDC = false;
                if (hSrcDC == IntPtr.Zero) {
                    hSrcDC = CreateDC("DISPLAY", null, null, IntPtr.Zero);
                    isCreatedDC = true;
                }

                if (hSrcDC == IntPtr.Zero) {
                    throw new Exception("Unable to acquire display device context.");
                }

                try {
                    using (Bitmap srcBmp = new Bitmap(srcW, srcH, PixelFormat.Format24bppRgb)) {
                        using (Graphics gSrc = Graphics.FromImage(srcBmp)) {
                            IntPtr hDestDC = gSrc.GetHdc();
                            try {
                                BitBlt(hDestDC, 0, 0, srcW, srcH, hSrcDC, 0, 0, SRCCOPY | CAPTUREBLT);
                            } finally {
                                gSrc.ReleaseHdc(hDestDC);
                            }

                            // High-Visibility Cursor Halo & Pointer Rendering
                            try {
                                gSrc.SmoothingMode = SmoothingMode.AntiAlias;

                                // 1. Draw glowing beacon ring around mouse hotspot
                                int r = 26; // 52px halo ring
                                using (Pen penHalo = new Pen(Color.FromArgb(200, 255, 215, 0), 4f)) // Golden glowing ring
                                using (Pen penDark = new Pen(Color.FromArgb(220, 15, 23, 42), 2f)) { // Dark contrast border
                                    gSrc.DrawEllipse(penDark, curX - r - 2, curY - r - 2, (r + 2) * 2, (r + 2) * 2);
                                    gSrc.DrawEllipse(penHalo, curX - r, curY - r, r * 2, r * 2);
                                    gSrc.DrawEllipse(penDark, curX - r + 2, curY - r + 2, (r - 2) * 2, (r - 2) * 2);
                                }

                                // 2. Draw actual cursor or high-contrast enlarged arrow
                                CURSORINFO ci = new CURSORINFO();
                                ci.cbSize = Marshal.SizeOf(typeof(CURSORINFO));
                                bool drewIcon = false;
                                if (GetCursorInfo(out ci) && ci.flags == CURSOR_SHOWING && ci.hCursor != IntPtr.Zero) {
                                    IntPtr hdcCursor = gSrc.GetHdc();
                                    try {
                                        drewIcon = DrawIconEx(hdcCursor, curX, curY, ci.hCursor, 42, 42, 0, IntPtr.Zero, 3);
                                        if (!drewIcon) DrawIcon(hdcCursor, curX, curY, ci.hCursor);
                                    } finally {
                                        gSrc.ReleaseHdc(hdcCursor);
                                    }
                                }

                                if (!drewIcon) {
                                    // Custom high-contrast arrow pointer (White body with black outline)
                                    Point[] arrowPoints = new Point[] {
                                        new Point(curX, curY),
                                        new Point(curX, curY + 30),
                                        new Point(curX + 8, curY + 24),
                                        new Point(curX + 15, curY + 36),
                                        new Point(curX + 20, curY + 33),
                                        new Point(curX + 14, curY + 21),
                                        new Point(curX + 23, curY + 21)
                                    };
                                    using (SolidBrush brushWhite = new SolidBrush(Color.White))
                                    using (Pen penBlack = new Pen(Color.Black, 2.5f)) {
                                        gSrc.FillPolygon(brushWhite, arrowPoints);
                                        gSrc.DrawPolygon(penBlack, arrowPoints);
                                    }
                                }
                            } catch { }
                        }

                        using (Bitmap destBmp = new Bitmap(destW, destH, PixelFormat.Format24bppRgb)) {
                            using (Graphics gDest = Graphics.FromImage(destBmp)) {
                                gDest.InterpolationMode = InterpolationMode.Bilinear;
                                gDest.PixelOffsetMode = PixelOffsetMode.Half;
                                gDest.DrawImage(srcBmp, 0, 0, destW, destH);
                            }

                            ImageCodecInfo jpgEncoder = GetEncoder(ImageFormat.Jpeg);
                            EncoderParameters encParams = new EncoderParameters(1);
                            encParams.Param[0] = new EncoderParameter(System.Drawing.Imaging.Encoder.Quality, (long)Math.Max(10, Math.Min(100, quality)));
                            using (MemoryStream ms = new MemoryStream()) {
                                destBmp.Save(ms, jpgEncoder, encParams);
                                return ms.ToArray();
                            }
                        }
                    }
                } finally {
                    if (isCreatedDC) {
                        DeleteDC(hSrcDC);
                    } else {
                        ReleaseDC(IntPtr.Zero, hSrcDC);
                    }
                }
            } catch (Exception) {
                // Return a clean fallback frame indicating screen unavailable / locked
                using (Bitmap fallbackBmp = new Bitmap(destW, destH, PixelFormat.Format24bppRgb)) {
                    using (Graphics g = Graphics.FromImage(fallbackBmp)) {
                        g.Clear(Color.FromArgb(11, 15, 25)); // #0B0F19 dark background
                        using (Font fontTitle = new Font(FontFamily.GenericSansSerif, 18, FontStyle.Bold))
                        using (Font fontSub = new Font(FontFamily.GenericSansSerif, 12, FontStyle.Regular))
                        using (Brush brushTitle = new SolidBrush(Color.FromArgb(99, 102, 241))) // Indigo #6366F1
                        using (Brush brushSub = new SolidBrush(Color.FromArgb(160, 174, 192)))
                        using (StringFormat sf = new StringFormat() { Alignment = StringAlignment.Center, LineAlignment = StringAlignment.Center }) {
                            g.DrawString("Remote Pocket", fontTitle, brushTitle, new RectangleF(0, destH / 2f - 40, destW, 30), sf);
                            g.DrawString("Desktop screen is locked or in standby", fontSub, brushSub, new RectangleF(0, destH / 2f + 5, destW, 30), sf);
                        }
                    }
                    ImageCodecInfo jpgEncoder = GetEncoder(ImageFormat.Jpeg);
                    EncoderParameters encParams = new EncoderParameters(1);
                    encParams.Param[0] = new EncoderParameter(System.Drawing.Imaging.Encoder.Quality, (long)Math.Max(10, Math.Min(100, quality)));
                    using (MemoryStream ms = new MemoryStream()) {
                        fallbackBmp.Save(ms, jpgEncoder, encParams);
                        return ms.ToArray();
                    }
                }
            }
        }

        public static byte[] CaptureScreenJpeg(int quality = 65, int targetHeight = 720) {
            int x, y, w, h;
            return CaptureScreenJpeg(quality, targetHeight, out x, out y, out w, out h);
        }

        private static ImageCodecInfo GetEncoder(ImageFormat format) {
            ImageCodecInfo[] codecs = ImageCodecInfo.GetImageDecoders();
            foreach (ImageCodecInfo codec in codecs) {
                if (codec.FormatID == format.Guid) {
                    return codec;
                }
            }
            return null;
        }

        // ==========================================
        // AUDIO HELPER (CoreAudioApi COM)
        // ==========================================
        private static IAudioEndpointVolume GetMasterVolumeEndpoint() {
            var enumerator = new MMDeviceEnumeratorComObject() as IMMDeviceEnumerator;
            IMMDevice dev;
            int hr = enumerator.GetDefaultAudioEndpoint(0, 1, out dev);
            if (hr < 0 || dev == null) return null;

            IntPtr volInterfacePtr;
            var volId = new Guid("5CDF2C82-841E-4546-9722-0CF74078229A");
            hr = dev.Activate(ref volId, 23, IntPtr.Zero, out volInterfacePtr);
            if (hr < 0 || volInterfacePtr == IntPtr.Zero) return null;

            return Marshal.GetObjectForIUnknown(volInterfacePtr) as IAudioEndpointVolume;
        }

        public static string GetAudioStatusJson() {
            try {
                var volumeEndpoint = GetMasterVolumeEndpoint();
                if (volumeEndpoint == null) {
                    return "{\"volume\":null,\"muted\":null,\"playing\":null,\"error\":\"Endpoint unavailable\"}";
                }

                float volLevel = 0;
                volumeEndpoint.GetMasterVolumeLevelScalar(out volLevel);
                bool isMuted = false;
                volumeEndpoint.GetMute(out isMuted);

                bool isPlaying = false;
                try {
                    var enumerator = new MMDeviceEnumeratorComObject() as IMMDeviceEnumerator;
                    IMMDevice dev;
                    if (enumerator.GetDefaultAudioEndpoint(0, 1, out dev) >= 0 && dev != null) {
                        IntPtr meterInterfacePtr;
                        var meterId = new Guid("C02216F6-8C67-4B5B-9786-D5C4DF585117");
                        if (dev.Activate(ref meterId, 23, IntPtr.Zero, out meterInterfacePtr) >= 0 && meterInterfacePtr != IntPtr.Zero) {
                            var meter = Marshal.GetObjectForIUnknown(meterInterfacePtr) as IAudioMeterInformation;
                            float peakValue = 0;
                            meter.GetPeakValue(out peakValue);
                            isPlaying = peakValue > 0.005f;
                        }
                    }
                } catch { }

                return string.Format("{{\"volume\":{0},\"muted\":{1},\"playing\":{2}}}",
                    Math.Round(volLevel * 100),
                    isMuted.ToString().ToLower(),
                    isPlaying.ToString().ToLower());
            } catch (Exception ex) {
                return string.Format("{{\"volume\":null,\"muted\":null,\"playing\":null,\"error\":\"{0}\"}}", ex.Message.Replace("\"", "\\\""));
            }
        }

        public static void SetAudioVolume(float levelPercent) {
            try {
                var endpoint = GetMasterVolumeEndpoint();
                if (endpoint == null) return;
                float scalar = Math.Max(0f, Math.Min(100f, levelPercent)) / 100.0f;
                Guid ctx = Guid.Empty;
                endpoint.SetMasterVolumeLevelScalar(scalar, ref ctx);
            } catch { }
        }

        public static void SetAudioMute(bool mute) {
            try {
                var endpoint = GetMasterVolumeEndpoint();
                if (endpoint == null) return;
                Guid ctx = Guid.Empty;
                endpoint.SetMute(mute, ref ctx);
            } catch { }
        }

        public static void ToggleAudioMute() {
            try {
                var endpoint = GetMasterVolumeEndpoint();
                if (endpoint == null) return;
                bool isMuted;
                endpoint.GetMute(out isMuted);
                Guid ctx = Guid.Empty;
                endpoint.SetMute(!isMuted, ref ctx);
            } catch { }
        }
    }
}
