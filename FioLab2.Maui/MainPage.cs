using FioLab2.Engine;
using SkiaSharp;
using SkiaSharp.Views.Maui;
using SkiaSharp.Views.Maui.Controls;

namespace FioLab2.Maui;

public sealed class MainPage : ContentPage
{
    private readonly Entry _textEntry;
    private readonly Label _fontLabel;
    private readonly Label _statusLabel;
    private readonly SKCanvasView _canvas;

    private readonly FioLab2Engine _engine =
        new();

    private SKTypeface? _typeface;
    private DigitizeResult? _result;

    public MainPage()
    {
        Title = "FioLab V2";

        BackgroundColor =
            Color.FromArgb("#07131E");

        _textEntry = new Entry
        {
            Text = "Maria",
            Placeholder = "Digite o nome",
            TextColor = Colors.White,
            PlaceholderColor =
                Color.FromArgb("#8493A3"),
            BackgroundColor =
                Color.FromArgb("#10202E")
        };

        _fontLabel = new Label
        {
            Text = "Nenhuma fonte importada",
            TextColor =
                Color.FromArgb("#C6D0DA"),
            FontSize = 13
        };

        _statusLabel = new Label
        {
            Text =
                "V2 limpa • ramos Satin independentes • sem motor legado",
            TextColor =
                Color.FromArgb("#EBC46B"),
            FontSize = 13
        };

        _canvas = new SKCanvasView
        {
            HeightRequest = 430,
            BackgroundColor =
                Color.FromArgb("#F6F0E3")
        };

        _canvas.PaintSurface +=
            OnPaintSurface;

        var importButton = new Button
        {
            Text = "Importar TTF/OTF",
            BackgroundColor =
                Color.FromArgb("#172A3A"),
            TextColor = Colors.White
        };

        importButton.Clicked +=
            OnImportFont;

        var generateButton = new Button
        {
            Text = "Gerar pontos V2",
            BackgroundColor =
                Color.FromArgb("#EBC46B"),
            TextColor =
                Color.FromArgb("#101820"),
            FontAttributes =
                FontAttributes.Bold
        };

        generateButton.Clicked +=
            OnGenerate;

        Content = new ScrollView
        {
            Content =
                new VerticalStackLayout
                {
                    Padding =
                        new Thickness(
                            18,
                            24),
                    Spacing = 14,
                    Children =
                    {
                        new Label
                        {
                            Text =
                                "FioLab V2 — Motor Limpo",
                            FontSize = 28,
                            FontAttributes =
                                FontAttributes.Bold,
                            TextColor =
                                Colors.White
                        },
                        new Label
                        {
                            Text =
                                "Geometria correta primeiro: cada ramo físico é concluído antes do próximo.",
                            TextColor =
                                Color.FromArgb("#9FB0BF")
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
                            Stroke =
                                Color.FromArgb("#D9C9A9"),
                            StrokeThickness = 1,
                            BackgroundColor =
                                Color.FromArgb("#F6F0E3"),
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
            var picked =
                await FilePicker.Default
                    .PickAsync(
                        new PickOptions
                        {
                            PickerTitle =
                                "Escolha uma fonte TTF ou OTF"
                        });

            if (picked is null)
            {
                return;
            }

            await using var source =
                await picked.OpenReadAsync();

            using var memory =
                new MemoryStream();

            await source.CopyToAsync(
                memory);

            memory.Position = 0;

            var typeface =
                SKTypeface.FromStream(
                    memory);

            if (typeface is null)
            {
                _statusLabel.Text =
                    "Arquivo não reconhecido como fonte.";

                return;
            }

            _typeface?.Dispose();
            _typeface = typeface;

            _fontLabel.Text =
                $"{picked.FileName} • {_typeface.FamilyName}";

            _statusLabel.Text =
                "Fonte carregada na V2. Gere a matriz.";
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
            var options =
                new DigitizeOptions(
                    FontSizePx: 180f,
                    RasterScale: 2.0f,
                    SatinRowSpacingPx: 2.6f,
                    TatamiRowSpacingPx: 3.8f,
                    MaxSatinWidthPx: 52f,
                    CompactFillMaxSizePx: 34f,
                    IncludeUnderlay: false);

            _result =
                _engine.Digitize(
                    _typeface,
                    _textEntry.Text ??
                    string.Empty,
                    options);

            _statusLabel.Text =
                $"{_result.Objects.Count} objetos • " +
                $"{_result.StitchCount} pontos • " +
                $"{_result.JumpCount} viagens • V2 sem intercalar";

            _canvas.InvalidateSurface();
        }
        catch (Exception exception)
        {
            _statusLabel.Text =
                $"Falha no motor V2: {exception.Message}";
        }
    }

    private void OnPaintSurface(
        object? sender,
        SKPaintSurfaceEventArgs e)
    {
        var canvas =
            e.Surface.Canvas;

        canvas.Clear(
            new SKColor(
                246,
                240,
                227));

        var result = _result;

        if (
            result is null ||
            result.Stitches.Count == 0 ||
            result.Bounds.IsEmpty)
        {
            return;
        }

        var bounds =
            result.Bounds;

        const float margin = 38f;

        var scaleX =
            (e.Info.Width -
             margin * 2f) /
            MathF.Max(
                1f,
                bounds.Width);

        var scaleY =
            (e.Info.Height -
             margin * 2f) /
            MathF.Max(
                1f,
                bounds.Height);

        var scale =
            MathF.Min(
                scaleX,
                scaleY);

        canvas.Save();

        canvas.Translate(
            margin -
            bounds.Left * scale,
            margin -
            bounds.Top * scale);

        canvas.Scale(
            scale,
            scale);

        using var stitchPaint =
            new SKPaint
            {
                Color =
                    new SKColor(
                        235,
                        57,
                        76),
                StrokeWidth =
                    MathF.Max(
                        0.7f,
                        1.35f / scale),
                Style =
                    SKPaintStyle.Stroke,
                IsAntialias = true,
                StrokeCap =
                    SKStrokeCap.Round
            };

        using var jumpPaint =
            new SKPaint
            {
                Color =
                    new SKColor(
                        105,
                        115,
                        125,
                        70),
                StrokeWidth =
                    MathF.Max(
                        0.35f,
                        0.70f / scale),
                Style =
                    SKPaintStyle.Stroke,
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
