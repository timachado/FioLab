# FioLab 0.3.0

Aplicativo Android nativo para trabalhar com matrizes de bordado diretamente pelo celular.

## Funcional nesta versão

- Abrir arquivos Tajima DST pelo seletor nativo do Android.
- Visualizar a matriz com zoom e arraste.
- Mostrar dimensões, total de pontos e blocos de cor.
- Simular a sequência progressiva do bordado.
- Play, pausa, avanço e retrocesso.
- Controle de velocidade de 0,5× a 5×.
- Mostrar percentual concluído e bloco atual.
- Estimativa inicial de consumo de linha.
- Salvar uma cópia em local escolhido pelo usuário.
- Selecionar memória interna, cartão SD ou pendrive OTG quando disponível no seletor do Android.
- Compartilhar a matriz por aplicativos compatíveis.
- Preservar o arquivo original sem alteração.

## Segurança de arquivos

O FioLab usa o Storage Access Framework do Android para salvar cópias. Não solicita acesso irrestrito ao armazenamento. Para compartilhamento, usa FileProvider com acesso temporário somente de leitura.

## APK

O GitHub Actions executa testes e compila o APK automaticamente.

Após uma compilação aprovada, o APK de teste é publicado em **Releases** como:

`FioLab-0.3.0-debug.apk`

## Compatibilidade da v0.3.0

- Android 8.0+ (minSdk 26)
- compileSdk/targetSdk 36
- JDK 17
- Android Gradle Plugin 9.4.0
- Gradle 9.6.0
- Jetpack Compose

## Próximas etapas

- 0.4: novos leitores de formatos validados individualmente.
- 0.5: conversão entre formatos.
- 0.6: editor básico.
- 0.7: Criar Nome e biblioteca de fontes de bordado.

Desenvolvido por **T.I. Machado — Soluções em Tecnologia**.
