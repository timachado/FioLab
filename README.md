# FioLab 0.6.0

Aplicativo Android nativo para trabalhar com matrizes de bordado diretamente pelo celular.

## Formatos liberados

- DST — Tajima.
- PES — Brother/Baby Lock.
- JEF — Janome/Elna.

## Editor

A v0.6.0 adiciona edição não destrutiva:

- mover em X/Y;
- redimensionar;
- girar;
- espelhar horizontalmente;
- espelhar verticalmente;
- centralizar a matriz;
- trocar cores por bloco;
- visualizar dimensões atualizadas em tempo real.

O editor trabalha sobre o modelo normalizado do FioLab. O arquivo original nunca é sobrescrito.

Ao aplicar uma edição, o FioLab mantém a matriz editada em memória e regenera um novo arquivo quando o usuário salva ou compartilha.

## Conversor

Conversão disponível:

**DST ⇄ PES ⇄ JEF**

A edição pode ser aplicada antes da conversão.

## Fluxo mobile

1. Abra a matriz.
2. Visualize e confira dimensões.
3. Edite se necessário.
4. Simule a sequência.
5. Salve no celular, SD ou pendrive OTG.
6. Compartilhe ou converta para outro formato.

## Validação

O CI executa:
- testes do parser DST;
- leitura de amostras reais PES/JEF;
- geração e reabertura DST/PES/JEF;
- testes de escala;
- testes de rotação;
- testes de espelhamento;
- testes de centralização;
- testes de troca de cores;
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
- ZIP contendo o APK para navegadores Android que travam na finalização de downloads APK.

## Próximos passos

- 0.7: Criar Nome e biblioteca inicial de fontes de bordado.
- depois: expansão para VP3, EXP, XXX, U01 e TBF.
- depois: recursos avançados de edição e otimização.

Desenvolvido por **T.I. Machado — Soluções em Tecnologia**.
