# FioLab 0.4.0

Aplicativo Android nativo para trabalhar com matrizes de bordado diretamente pelo celular.

## Formatos liberados

- DST — parser próprio FioLab.
- JEF — Janome/Elna.
- PES — Brother.

JEF e PES são normalizados para o mesmo modelo interno usado pelo visualizador e simulador do FioLab.

## Funcional nesta versão

- Abrir DST, JEF e PES pelo seletor nativo do Android.
- Visualizar matriz com zoom e arraste.
- Mostrar dimensões, pontos e blocos de cor.
- Simular sequência progressiva de bordado.
- Play, pausa, avanço, retrocesso e velocidade.
- Estimar consumo de linha.
- Salvar cópia na memória, SD ou pendrive OTG quando disponível.
- Compartilhar matriz por aplicativos compatíveis.
- Preservar arquivo original.

## Validação de formatos

O CI inclui arquivos binários reais JEF e PES e exige que ambos sejam lidos antes de publicar o APK.

## Engine de compatibilidade

Para formatos complexos, o FioLab usa EmbroideryIO, biblioteca Java/Android sob licença MIT, mantendo uma camada própria de normalização e renderização.

Consulte `THIRD_PARTY_NOTICES.md`.

## Compatibilidade

- Android 8.0+.
- minSdk 26.
- compileSdk/targetSdk 36.
- JDK 17.
- Gradle 9.6.0.
- Jetpack Compose.

## Próximos passos

- 0.5: conversão real entre formatos.
- 0.6: editor básico.
- 0.7: Criar Nome e fontes de bordado.

Desenvolvido por **T.I. Machado — Soluções em Tecnologia**.
