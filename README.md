# FioLab 0.7.0

Aplicativo Android nativo para criar e trabalhar com matrizes de bordado diretamente pelo celular.

## Criar Nome

A v0.7 adiciona geração real de nomes em pontadas.

Fluxo:

1. Toque em **Criar nome**.
2. Digite até 24 caracteres.
3. Escolha o preset de fonte de bordado.
4. Ajuste altura, espaçamento e comprimento do ponto.
5. Escolha a cor.
6. Escolha DST, PES ou JEF.
7. Confira a prévia.
8. Simule ou crie a matriz.
9. Salve no celular, SD ou pendrive OTG.

### Presets iniciais

- **Fio Linha**
- **Fio Compacta**
- **Fio Larga**

Eles usam um alfabeto desenhado especificamente como trajetórias de costura, com A–Z, números, pontuação básica e acentos do português.

## Por que não usar TTF diretamente?

Uma fonte de tela não contém densidade, sequência de bordado, saltos, arremates ou regras de máquina.

Por isso a v0.7 começa com uma fonte de bordado própria, onde cada letra já é descrita como caminho de costura.

## Formatos liberados

- DST — Tajima.
- PES — Brother/Baby Lock.
- JEF — Janome/Elna.

## Editor

Matrizes abertas ou criadas podem:
- mover;
- redimensionar;
- girar;
- espelhar;
- centralizar;
- trocar cores;
- simular;
- converter;
- salvar;
- compartilhar.

## Conversor

Conversão disponível:

**DST ⇄ PES ⇄ JEF**

## Validação no CI

O pipeline exige:
- parser DST;
- leitura de amostras reais PES/JEF;
- geração e reabertura DST/PES/JEF;
- testes do editor;
- geração de nomes em pontadas;
- teste de acentos;
- teste dos presets;
- exportação do nome para os três formatos;
- compilação Android.

## Compatibilidade

- Android 8.0+.
- minSdk 26.
- compileSdk/targetSdk 36.
- JDK 17.
- Gradle 9.6.0.
- Jetpack Compose.

## Downloads

Cada Release publica:
- APK direto;
- ZIP contendo o APK para navegadores Android que travam na finalização de APK.

## Próximos passos

- ampliar a biblioteca de fontes de bordado;
- criar monogramas;
- adicionar bastidores e validação de tamanho;
- expandir formatos para VP3, EXP, XXX, U01 e TBF;
- evoluir pontos satin/tatami para criação mais avançada.

Desenvolvido por **T.I. Machado — Soluções em Tecnologia**.
