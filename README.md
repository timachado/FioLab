# FioLab 0.5.0

Aplicativo Android nativo para matrizes de bordado diretamente pelo celular.

## Formatos liberados

- DST — Tajima.
- PES — Brother/Baby Lock.
- JEF — Janome/Elna.

## Conversor

A v0.5.0 permite converter:

**DST ⇄ PES ⇄ JEF**

Fluxo:

1. Abra uma matriz.
2. Toque em **Converter**.
3. Escolha o formato de destino.
4. Use **Converter e salvar** ou **Converter e compartilhar**.
5. Ao salvar, escolha memória interna, cartão SD ou pendrive OTG disponível no seletor do Android.

O arquivo original nunca é sobrescrito.

## Cores

PES/JEF podem trazer informações de cores de linha e o FioLab preserva essa paleta quando disponível.

DST normalmente não carrega uma paleta de linha confiável. Ao converter DST para PES/JEF, o FioLab mantém os blocos/trocas e aplica uma paleta padrão para que a estrutura de cores não seja perdida.

## Validação

O CI executa:
- teste do parser DST;
- leitura de amostras reais PES/JEF;
- conversão para DST, PES e JEF;
- reabertura dos arquivos convertidos;
- compilação Android.

## Compatibilidade

- Android 8.0+.
- minSdk 26.
- compileSdk/targetSdk 36.
- JDK 17.
- Gradle 9.6.0.
- Jetpack Compose.

## Download

Cada Release publica:
- APK direto;
- ZIP contendo o APK, como alternativa para navegadores Android.

## Próximos passos

- 0.6: editor básico.
- 0.7: Criar Nome e biblioteca de fontes de bordado.
- expansão gradual para VP3, EXP, XXX, U01 e TBF.

Desenvolvido por **T.I. Machado — Soluções em Tecnologia**.
