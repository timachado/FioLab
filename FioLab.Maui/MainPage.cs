using FioLab.Engine;
using SkiaSharp;
using SkiaSharp.Views.Maui;
using SkiaSharp.Views.Maui.Controls;

namespace FioLab.Maui;

public sealed class MainPage : ContentPage
{
    private readonly Entry _textEntry;
    private readonly Label _fontLabel;
    private readonly Label _statusLabel;
    private readonly SKCanvasView _canvas;

    private readonly SkiaEmbroideryEngine _engine = new();

    private SKTypeface? _typeface;
    private DigitizeResult? _result;

    public MainPage()
    {
        Title = "FioLab MAUI";

        BackgroundColor = Color.FromArgb("#07131E");

        _textEntry = new Entry
        {
            Text = "Maria",
            Placeholder = "Digite o nome",
            TextColor = Colors.White,
            PlaceholderColor = Color.FromArgb("#8493A3"),
            BackgroundColor = Color.FromArgb("#10202E")
        };

        _fontLabel = new Label
        {
            Text = "Nenhuma fonte importada",
            TextColor = Color.FromArgb("#C6D0DA"),
            FontSize = 13
        };

        _statusLabel = new Label
        {
            Text = ".NET MAUI 10 + SkiaSharp • motor novo",
            TextColor = Color.FromArgb("#EBC46B"),
            FontSize = 13
        };

        _canvas = new SKCanvasView
        {
            HeightRequest = 430,
            BackgroundColor = Color.FromArgb("#F6F0E3")
        };

        _canvas.PaintSurface += OnPaintSurface;

        var importButton = new Button
        {
            Text = "Importar TTF/OTF",
            BackgroundColor = Color.FromArgb("#172A3A"),
            TextColor = Colors.White
        };

        importButton.Clicked += OnImportFont;

        var generateButton = new Button
        {
            Text = "Gerar pontos",
            BackgroundColor = Color.FromArgb("#EBC46B"),
            TextColor = Color.FromArgb("#101820"),
            FontAttributes = FontAttributes.Bold
        };

        generateButton.Clicked += OnGenerate;

        Content = new ScrollView
        {
            Content = new VerticalStackLayout
            {
                Padding = new Thickness(18, 24),
                Spacing = 14,
                Children =
                {
                    new Label
                    {
                        Text = "FioLab — Motor Skia",
                        FontSize = 28,
                        FontAttributes = FontAttributes.Bold,
                        TextColor = Colors.White
                    },
                    new Label
                    {
                        Text = "Objeto completo antes de mudar o trajeto.",
                        TextColor = Color.FromArgb("#9FB0BF")
                    },
                    _textEntry,
                    new HorizontalStackLayout
                    {
                        Spacing = 10,
                        Children =
                        {
                            importButton,
                            generateButton
                        }
                    },
                    _fontLabel,
                    _statusLabel,
                    new Border
                    {
                        Stroke = Color.FromArgb("#D9C9A9"),
                        StrokeThickness = 1,
                        BackgroundColor = Color.FromArgb("#F6F0E3"),
                        Padding = 0,
                        Content = _canvas
                    }
                }
            }
        };
    }

    private async void OnImportFont(
        object? sender,
        EventArgs e)
    {
        try
        {
            var picked = await FilePicker.Default.PickAsync(
                new PickOptions
                {
                    PickerTitle = "Escolha uma fonte TTF ou OTF"
                });

            if (picked is null)
            {
                return;
            }

            await using var source = await picked.OpenReadAsync();
            using var memory = new MemoryStream();

            await source.CopyToAsync(memory);
            memory.Position = 0;

            var typeface = SKTypeface.FromStream(memory);

            if (typeface is null)
            {
                _statusLabel.Text = "Arquivo não reconhecido como fonte.";
                return;
            }

            _typeface?.Dispose();
            _typeface = typeface;

            _fontLabel.Text =
                $"{picked.FileName} • {_typeface.FamilyName}";

            _statusLabel.Text =
                "Fonte carregada. Gere novamente a matriz.";
        }
        catch (Exception exception)
        {
            _statusLabel.Text =
                $"Erro ao importar: {exception.Message}";
        }
    }

    private void OnGenerate(
        object? sender,
        EventArgs e)
    {
        if (_typeface is null)
        {
            _statusLabel.Text =
                "Importe primeiro a Alliby ou outra TTF/OTF.";
            return;
        }

        try
        {
            var options = new DigitizeOptions(
                FontSizePx: 180f,
                RasterScale: 1.5f,
                DensityPx: 3.0f,
                TatamiDensityPx: 4.0f,
                StitchLengthPx: 12f,
                RunningMaxWidthPx: 4f,
                MaxSatinWidthPx: 42f,
                IncludeUnderlay: true);

            _result = _engine.Digitize(
                _typeface,
                _textEntry.Text ?? string.Empty,
                options);

            _statusLabel.Text =
                $"{_result.Objects.Count} objetos • {_result.StitchCount} pontos • sem intercalar objetos";

            _canvas.InvalidateSurface();
        }
        catch (Exception exception)
        {
            _statusLabel.Text =
                $"Falha no motor: {exception.Message}";
        }
    }

    private void OnPaintSurface(
        object? sender,
        SKPaintSurfaceEventArgs e)
    {
        var canvas = e.Surface.Canvas;
        canvas.Clear(new SKColor(246, 240, 227));

        var result = _result;

        if (
            result is null ||
            result.Stitches.Count == 0 ||
            result.Bounds.IsEmpty)
        {
            return;
        }

        var bounds = result.Bounds;
        var margin = 38f;

        var scaleX =
            (e.Info.Width - margin * 2f) /
            MathF.Max(1f, bounds.Width);

        var scaleY =
            (e.Info.Height - margin * 2f) /
            MathF.Max(1f, bounds.Height);

        var scale = MathF.Min(scaleX, scaleY);

        canvas.Save();
        canvas.Translate(
            margin - bounds.Left * scale,
            margin - bounds.Top * scale);

        canvas.Scale(scale, scale);

        using var stitchPaint = new SKPaint
        {
            Color = new SKColor(235, 57, 76),
            StrokeWidth = MathF.Max(0.7f, 1.4f / scale),
            Style = SKPaintStyle.Stroke,
            IsAntialias = true,
            StrokeCap = SKStrokeCap.Round
        };

        using var jumpPaint = new SKPaint
        {
            Color = new SKColor(120, 120, 120, 80),
            StrokeWidth = MathF.Max(0.4f, 0.8f / scale),
            Style = SKPaintStyle.Stroke,
            IsAntialias = true
        };

        StitchPoint? previous = null;

        foreach (var point in result.Stitches)
        {
            if (previous is not null)
            {
                var sameObject =
                    previous.Value.ObjectIndex ==
                    point.ObjectIndex;

                var paint =
                    sameObject &&
                    point.Command ==
                    StitchCommand.Stitch
                        ? stitchPaint
                        : jumpPaint;

                canvas.DrawLine(
                    previous.Value.X,
                    previous.Value.Y,
                    point.X,
                    point.Y,
                    paint);
            }

            previous = point;
        }

        canvas.Restore();
    }
}
