# Changelog

## 0.14.0

### Texto Curvo
- Composição Reta.
- Arco para cima.
- Arco para baixo.
- Altura do arco configurável.
- Cada letra acompanha a tangente do arco.

### Ajuste por Letra
- Seleção individual de cada letra.
- Movimento horizontal independente.
- Movimento vertical independente.
- Rotação de -45° a 45°.
- Espaçamento adicional ou negativo após cada letra.
- Redefinição individual.
- Saltos e TRIM continuam pertencendo à sequência real de costura.

### Centralização
- Após todas as transformações, a matriz é recentralizada automaticamente no 0,0.
- Validação de bastidor acontece sobre a geometria final.
- Prévia, simulador e arquivos DST/PES/JEF usam a mesma composição.

### Testes
- Arco para cima versus texto reto.
- Arco para cima versus arco para baixo.
- Rotação individual.
- Espaçamento individual.
- Centralização final.
- Exportação de texto curvo para DST/PES/JEF.


## 0.13.0

### Biblioteca de Fontes
- Nova tela Biblioteca de Fontes acessível pela Home.
- 7 famílias: Fio Linha, Compacta, Larga, Moderna, Itálica, Elegante e Ornamental.
- Cada família altera a geometria das letras, não apenas o nome do preset.
- Inclinação real nas famílias Itálica/Elegante.
- Serifas geradas como trajetórias extras nas famílias Elegante/Ornamental.
- Prolongamentos decorativos na Fio Ornamental.
- Prévia Satin de cada família dentro da biblioteca.
- Categoria e descrição de uso para cada fonte.
- Biblioteca integrada ao Criar Nome e Criar Monograma.
- Testes garantindo geometrias distintas entre as famílias.
- Testes de exportação de todas as fontes para DST/PES/JEF.


## 0.12.0

### Monogramas
- Novo fluxo Criar Monograma.
- 1, 2 ou 3 iniciais.
- Estilo Linear.
- Estilo Clássico com inicial central maior em monogramas de 3 letras.
- Estilo Empilhado.
- Geração Satin real por inicial.
- TRIM automático quando a distância entre letras exige deslocamento maior.
- Seleção de fonte de bordado.
- Altura e espaçamento configuráveis.
- Largura, densidade, compensação, short stitches e underlay Satin.
- Perfis de tecido e bastidor reaproveitados da v0.11.
- Validação de área segura antes de Criar/Simular.
- Prévia em escala de bastidor.
- Simulação da sequência real de costura.
- Exportação DST, PES e JEF.
- Testes para 1, 2 e 3 iniciais, layouts, bastidor e exportação.


## 0.11.0

### Bastidores
- Perfis genéricos 100×100, 130×180, 140×200, 160×260 e 200×300 mm.
- Margem de segurança padrão de 5 mm por lado.
- Área útil exibida antes de gerar a matriz.
- Prévia em proporção física do bastidor.
- Aviso de excesso em largura e altura.
- Criar e Simular ficam bloqueados quando a matriz ultrapassa a área segura.
- Bastidor selecionado acompanha a matriz criada e a Simulação de Máquina.

### Perfis iniciais de tecido
- Algodão.
- Malha / camiseta.
- Toalha / felpudo.
- Jeans / sarja.
- Boné estruturado.
- Cada perfil sugere densidade Satin, pull compensation, underlay e short stitches.
- Valores continuam editáveis pelo usuário depois da seleção.

### Qualidade
- Testes de encaixe e rejeição por bastidor.
- Testes dos valores iniciais dos perfis.
- Metadados de bastidor e tecido preservados na matriz criada.

### Importante
- Os perfis são pontos de partida; estabilizador, tensão, fio, agulha, máquina e tecido real continuam exigindo teste de costura.


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
