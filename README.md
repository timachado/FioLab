# FioLab 0.14.0

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

## Texto Curvo e Ajuste por Letra

A v0.14 adiciona um motor de composição por letra.

Modos:
- Reto;
- Arco para cima;
- Arco para baixo.

Cada letra pode receber:
- deslocamento horizontal;
- deslocamento vertical;
- rotação independente;
- espaçamento próprio após a letra.

No modo curvo, a letra também acompanha a tangente do arco. Depois das transformações, a matriz inteira é recentralizada automaticamente antes da validação de bastidor e exportação.

## Biblioteca de Fontes

A v0.13 cria uma biblioteca nativa de fontes de bordado com 7 famílias:
- Fio Linha;
- Fio Compacta;
- Fio Larga;
- Fio Moderna;
- Fio Itálica;
- Fio Elegante;
- Fio Ornamental.

As famílias alteram a geometria que será convertida em pontadas: largura, espaçamento, inclinação, serifas e prolongamentos decorativos. A Fio Ornamental foi pensada especialmente para monogramas.

A Home possui uma tela própria de Biblioteca, com prévia Satin gerada pelo mesmo motor usado nos arquivos finais.

## Monogramas

A v0.12 adiciona **Criar Monograma** com 1, 2 ou 3 iniciais.

Estilos:
- **Linear** — todas as letras com proporção semelhante;
- **Clássico** — em 3 iniciais, a letra central recebe maior destaque;
- **Empilhado** — composição vertical compacta.

Cada inicial é gerada como Satin real e depois posicionada na composição. O monograma usa os mesmos controles de tecido, bastidor, densidade, compensação, short stitches e underlay disponíveis no Criar Nome.

O resultado pode ser:
- visualizado;
- simulado;
- editado;
- convertido;
- salvo;
- compartilhado em DST, PES ou JEF.

## Bastidores e Perfis de Tecido

A v0.11 adiciona bastidores genéricos:
- 100×100 mm;
- 130×180 mm;
- 140×200 mm;
- 160×260 mm;
- 200×300 mm.

O FioLab reserva uma margem de segurança padrão de 5 mm por lado. A prévia é desenhada na proporção física do bastidor e informa se a matriz ultrapassa a área segura. Quando ultrapassa, **Criar matriz** e **Simular agora** ficam bloqueados.

Perfis iniciais de tecido:
- Algodão;
- Malha / camiseta;
- Toalha / felpudo;
- Jeans / sarja;
- Boné estruturado.

Selecionar um tecido aplica uma sugestão inicial de densidade Satin, compensação de repuxo, underlay e short stitches. Esses valores continuam editáveis e devem ser confirmados com teste de costura.

## Satin Refinado

A v0.10 melhora a geração Satin com:
- suavização de normal/tangente em cantos;
- compensação manual de repuxo;
- short stitches no lado interno da curva;
- underlay central;
- underlay zigue-zague;
- opção de combinar os dois underlays.

A prévia, o simulador e os arquivos DST/PES/JEF usam a mesma sequência de pontadas.

## Criar Nome com Satin

A v0.9 adiciona **Satin real** aos nomes criados no FioLab.

O usuário pode escolher:
- **Ponto corrido**;
- **Satin**.

No Satin o FioLab gera:
- underlay central opcional;
- coluna de zigue-zague real;
- largura configurável;
- densidade configurável;
- centenas de pontadas quando necessário.

Essas são as mesmas pontadas enviadas para DST/PES/JEF e mostradas na Simulação de Máquina. O simulador não aplica um efeito visual falso.

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

- monogramas;
- perfis personalizados de máquina/bastidor;
- biblioteca maior de fontes de bordado;
- expansão para VP3, EXP, XXX, U01 e TBF;
- validação de qualidade com amostras reais de costura.

Desenvolvido por **T.I. Machado — Soluções em Tecnologia**.
