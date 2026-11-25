using System;
using System.Drawing;
using System.IO;
using System.Reflection;
using System.Runtime.InteropServices;
using System.Windows.Forms;
using Microsoft.Web.WebView2.Core;
using Microsoft.Web.WebView2.WinForms;

namespace RCBeamDesignerQGU;

internal static class Program
{
    [STAThread]
    private static void Main()
    {
        ApplicationConfiguration.Initialize();
        Application.Run(new MainForm());
    }
}

internal sealed class MainForm : Form
{
    private const int DWMWA_CAPTION_COLOR = 35;
    private const int DWMWA_TEXT_COLOR = 36;

    [DllImport("dwmapi.dll")]
    private static extern int DwmSetWindowAttribute(IntPtr hwnd, int attr, ref int attrValue, int attrSize);

    private readonly WebView2 _webView;

    public MainForm()
    {
        Text = "مصمم الجسور الخرسانية المسلحة — جامعة القاسم الخضراء (ACI 318-19)";
        Width = 1360;
        Height = 840;
        MinimumSize = new Size(440, 620);
        StartPosition = FormStartPosition.CenterScreen;
        BackColor = Color.FromArgb(243, 246, 250);

        try
        {
            var exeIcon = Icon.ExtractAssociatedIcon(Application.ExecutablePath);
            if (exeIcon != null)
            {
                Icon = exeIcon;
            }
        }
        catch
        {
            // Ignore icon load failures
        }

        _webView = new WebView2
        {
            Dock = DockStyle.Fill,
            DefaultBackgroundColor = Color.FromArgb(243, 246, 250)
        };

        Controls.Add(_webView);

        HandleCreated += (_, _) => ApplyWindows11TitleBarTheme();
        Load += async (_, _) => await InitializeWebViewAsync();
    }

    private void ApplyWindows11TitleBarTheme()
    {
        try
        {
            // QGU Deep Navy (#16324F) in COLORREF (0x00BBGGRR)
            int captionColor = 0x004F3216;
            int textColor = 0x00FFFFFF;
            DwmSetWindowAttribute(Handle, DWMWA_CAPTION_COLOR, ref captionColor, sizeof(int));
            DwmSetWindowAttribute(Handle, DWMWA_TEXT_COLOR, ref textColor, sizeof(int));
        }
        catch
        {
            // Older Windows builds ignore custom caption colors safely
        }
    }

    private async System.Threading.Tasks.Task InitializeWebViewAsync()
    {
        try
        {
            string appData = Path.Combine(
                Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
                "RCBeamDesignerQGU"
            );
            string wwwDir = Path.Combine(appData, "www");
            string userDataDir = Path.Combine(appData, "WebView2Data");
            Directory.CreateDirectory(wwwDir);
            Directory.CreateDirectory(userDataDir);

            string htmlPath = Path.Combine(wwwDir, "index.html");
            using (Stream? stream = Assembly.GetExecutingAssembly().GetManifestResourceStream("RCBeamDesignerQGU.index.html"))
            {
                if (stream != null)
                {
                    using FileStream fs = new FileStream(htmlPath, FileMode.Create, FileAccess.Write);
                    await stream.CopyToAsync(fs);
                }
            }

            CoreWebView2Environment env = await CoreWebView2Environment.CreateAsync(null, userDataDir);
            await _webView.EnsureCoreWebView2Async(env);

            var settings = _webView.CoreWebView2.Settings;
            settings.AreDevToolsEnabled = false;
            settings.IsStatusBarEnabled = false;
            settings.AreDefaultContextMenusEnabled = true;
            settings.IsZoomControlEnabled = true;

            _webView.CoreWebView2.SetVirtualHostNameToFolderMapping(
                "rcbeam.qgu.local",
                wwwDir,
                CoreWebView2HostResourceAccessKind.Allow
            );

            _webView.CoreWebView2.Navigate("https://rcbeam.qgu.local/index.html");
        }
        catch (Exception ex)
        {
            MessageBox.Show(
                "تعذّر تشغيل واجهة التطبيق. تأكد من توفر Microsoft Edge WebView2 Runtime على ويندوز.\n\n" + ex.Message,
                "RC Beam Designer — ACI 318-19",
                MessageBoxButtons.OK,
                MessageBoxIcon.Error
            );
        }
    }
}
