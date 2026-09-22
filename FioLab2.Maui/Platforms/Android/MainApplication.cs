using Android.App;
using Android.Runtime;

namespace FioLab2.Maui;

[Application]
public sealed class MainApplication :
    MauiApplication
{
    public MainApplication(
        IntPtr handle,
        JniHandleOwnership ownership)
        : base(
            handle,
            ownership)
    {
    }

    protected override MauiApp CreateMauiApp() =>
        MauiProgram.CreateMauiApp();
}
