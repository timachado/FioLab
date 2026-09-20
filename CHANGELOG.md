# Changelog

## 0.10.0

### Satin Refinado
- Suavização da direção das colunas em cantos e mudanças de segmento.
- Compensação de repuxo configurável de 0 a 1 mm.
- Short stitches no lado interno de cantos fechados.
- Underlay configurável: nenhum, central, zigue-zague ou ambos.
- Underlay zigue-zague mais aberto antes da cobertura principal.
- Mesmas pontadas usadas na prévia, simulação e exportação.
- Testes específicos para compensação, short stitches e underlay.

### Limite atual
- O algoritmo foi refinado, mas ainda não substitui validação em tecido/máquina.
- Perfis automáticos por tecido e compensação dinâmica ficam para uma etapa posterior.


## 0.9.0

### Adicionado
- Gerador Satin real no Criar Nome.
- Alternância de pontadas lado a lado formando coluna satin.
- Largura Satin configurável de 1 a 6 mm.
- Densidade configurável de 0,3 a 1,2 mm.
- Underlay central opcional antes do preenchimento.
- Modo Ponto Corrido preservado como alternativa.
- Prévia com as pontadas Satin reais.
- Simulação de Máquina reproduzindo o preenchimento progressivo.
- Exportação Satin para DST, PES e JEF.
- Testes de densidade, underlay e exportação.

### Observação
- Esta é a primeira geração Satin do FioLab, baseada nos caminhos próprios das letras.
- Curvas e encontros complexos ainda serão refinados em versões seguintes para melhorar compensação, cantos e acabamento profissional.


## 0.8.0

### Adicionado
- Simulação de máquina redesenhada com referência visual completa ao fundo.
- Pontadas reais surgindo progressivamente sobre a referência.
- Marcador visual da posição atual da agulha.
- Canvas claro com grade e bastidor tracejado.
- Velocidades 1×, 2× e 4×.
- Ritmo base de aproximadamente 750 pontos/minuto em 1×.
- Tempo restante estimado durante a costura.
- Tempo total estimado.
- Contagem de pontos concluídos.
- Bloco/cor atual.
- Retorno de 10% da simulação.
- Botão parar e reiniciar.
- Saltos, cortes e trocas de cor com tempos visuais diferentes.
- Testes unitários do modelo de tempo da simulação.

### Comportamento importante
- O simulador representa as pontadas que realmente existem na matriz.
- Uma matriz satin aparece preenchendo progressivamente as letras.
- Uma matriz de ponto corrido permanece como ponto corrido.
- O FioLab não desenha preenchimento falso que não exista no arquivo.

## 0.7.0

### Adicionado
- Criar Nome diretamente pelo Android.
- Fonte de bordado própria baseada em caminhos de costura.
- A–Z, números, sinais básicos e acentos do português.
- Presets Fio Linha, Fio Compacta e Fio Larga.
- Controle de altura, espaçamento, ponto, cor e formato.
- Prévia, simulação e exportação DST/PES/JEF.

## 0.6.0

### Adicionado
- Editor básico de matrizes.
- Redimensionamento, rotação, movimento, espelhamento e cores.

## 0.5.0

### Adicionado
- Conversão real DST ⇄ PES ⇄ JEF.

## 0.4.0

### Adicionado
- Leitura real JEF/PES.

## 0.3.0

### Adicionado
- Salvar no Android, SD/OTG e compartilhar.

## 0.2.0

### Adicionado
- Primeiro simulador progressivo.
