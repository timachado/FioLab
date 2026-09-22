# FioLab V2 — reescrita limpa

A V2 existe em paralelo ao motor legado. Nenhum arquivo de `FioLab.Engine` é referenciado por `FioLab2.Engine`.

## Princípios

1. Geometria correta antes de reduzir a quantidade de objetos.
2. Cada ramo físico do medial axis vira uma coluna Satin independente.
3. Barras Satin são apenas cobertura; deslocamentos entre barras/objetos são tratados separadamente.
4. Um deslocamento que sair da forma ou cruzar pontos já emitidos vira `Jump`.
5. Ramos não são fundidos por heurística de proximidade.
6. Ornamentos compactos desconectados usam Tatami simples.
7. A CI bloqueia auto-cruzamento em reta, curva, junção em T e loop.

## Pipeline

TTF/OTF → glyph path Skia → raster binário → componentes → thinning → grafo do medial axis → poda conservadora de micro-spurs → colunas Satin independentes → pontos.

## Compatibilidade inicial

- .NET 10
- .NET MAUI 10
- SkiaSharp 4.151.2
- Android 24+
