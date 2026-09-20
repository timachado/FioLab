# FioLab 0.8.0

Aplicativo Android nativo para criar, visualizar, editar, simular e converter matrizes de bordado diretamente pelo celular.

## Simulação de Máquina

A v0.8 redesenha o simulador para se aproximar do comportamento visual de uma bordadeira real.

A tela mostra:
- a matriz completa de forma suave ao fundo;
- as pontadas já costuradas em destaque;
- a posição atual da agulha;
- o bloco/cor em execução;
- pontos concluídos;
- percentual;
- tempo restante;
- velocidade;
- consumo estimado de linha.

### Velocidade

O modo 1× usa aproximadamente **750 pontos por minuto** como referência.

Também existem 2× e 4× para acelerar a visualização.

Trocas de cor, cortes e saltos usam pausas diferentes para tornar a sequência mais parecida com o funcionamento de uma máquina.

## Fidelidade

O simulador não inventa preenchimento.

Se o arquivo contém satin/tatami, as pontadas densas aparecem preenchendo o desenho progressivamente.

Se a matriz contém somente ponto corrido, a simulação mostra ponto corrido.

Isso é importante para que a tela represente o que realmente será enviado para a máquina.

## Criar Nome

A v0.7 continua disponível:
- Fio Linha;
- Fio Compacta;
- Fio Larga;
- altura;
- espaçamento;
- comprimento de ponto;
- cor;
- DST/PES/JEF;
- acentos do português.

As fontes atuais são de trajetória/ponto corrido. Uma etapa futura adicionará geração satin para nomes.

## Formatos liberados

- DST — Tajima
- PES — Brother/Baby Lock
- JEF — Janome/Elna

## Editor

- mover;
- redimensionar;
- girar;
- espelhar;
- centralizar;
- trocar cores.

## Conversor

**DST ⇄ PES ⇄ JEF**

## Validação no CI

O pipeline exige:
- parser DST;
- amostras reais PES/JEF;
- conversão e reabertura dos formatos;
- testes do editor;
- geração de nomes;
- exportação de nomes;
- testes de tempo da simulação;
- compilação Android.

## Compatibilidade

- Android 8.0+
- minSdk 26
- compileSdk/targetSdk 36
- JDK 17
- Gradle 9.6.0
- Jetpack Compose

## Downloads

Cada Release publica:
- APK direto;
- ZIP contendo o APK.

## Próximos passos

- geração satin para nomes;
- monogramas;
- bastidores e validação de área;
- expansão para VP3, EXP, XXX, U01 e TBF.

Desenvolvido por **T.I. Machado — Soluções em Tecnologia**.
