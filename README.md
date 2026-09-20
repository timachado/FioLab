# FioLab 0.2.0

Aplicativo Android nativo para trabalhar com matrizes de bordado diretamente pelo celular.

## Estado atual

A v0.2.0 inclui:
- leitura real de matrizes Tajima DST;
- visualização em Canvas com zoom e arraste;
- dimensões, pontos e blocos de cor;
- simulador progressivo de bordado;
- play/pausa, avanço e retrocesso;
- controle de velocidade de 0,5× a 5×;
- progresso e bloco atual;
- estimativa inicial de consumo de linha;
- seleção de arquivos pelo mecanismo nativo do Android.

## APK

O APK de teste é compilado automaticamente pelo GitHub Actions e publicado em **Releases** como `FioLab-0.2.0-debug.apk`.

## Compatibilidade

- Android 8.0+ (minSdk 26)
- compileSdk 37
- JDK 17
- Android Gradle Plugin 9.4.0
- Gradle 9.6.0
- Jetpack Compose

## Código-fonte

O pacote integral desta versão está em:

`source/FioLab-0.2.0-source.zip`

## Próximas etapas

- 0.3: salvar/exportar no Android e pendrive OTG.
- 0.4: novos leitores de formatos, validados individualmente.
- 0.5: conversão.
- 0.6: editor básico.
- 0.7: Criar Nome e biblioteca de fontes de bordado.

Desenvolvido por **T.I. Machado — Soluções em Tecnologia**.