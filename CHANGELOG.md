# Changelog

## 0.21.5

### Fontes TTF/OTF
- Adicionada importação de fontes .ttf e .otf pela Biblioteca de Fontes.
- A fonte é validada antes do cadastro e copiada para o armazenamento privado do aplicativo.
- Limite de 12 MB por arquivo e bloqueio de extensões fora de TTF/OTF.
- Fontes importadas podem ser visualizadas e excluídas na própria biblioteca.
- Fontes importadas aparecem em Criar Nome sem remover ou substituir as famílias nativas do FioLab.
- O desenho vetorial dos glifos é extraído da fonte e convertido em trajetórias de bordado.
- Mantidos altura, espaçamento, ponto corrido/Satin, densidade, underlay, tecido, bastidor, cores, arco e ajustes por letra.
- A fonte importada preserva maiúsculas/minúsculas e caracteres suportados pelo próprio arquivo de fonte.

### Preservado
- Fontes nativas FioLab e o motor de texto existente.
- Geração, simulação, edição, exportação e transferência já existentes.
- Minha Conta/Supabase continua isolada da inicialização.
- Identidade visual e recursos de logo/launcher da 0.21.4.
- Pipeline Android validado com testes unitários e build debug antes da publicação.


## 0.21.3

### Hotfix de logo e inicialização
- Substituído o recurso de imagem corrompido por uma cópia válida da arte original FioLab Matrizes.
- A mesma arte válida é usada na Home e como ícone/roundIcon do aplicativo.
- Removida a dependência do mipmap corrompido da versão anterior.
- Mantido o isolamento de Minha Conta/Supabase da inicialização da Home.
- Mantido Ktor 3.2.2 com Supabase 3.2.x.


## 0.21.4

### Correção definitiva do empacotamento da logo
- A arte da marca passa a ser versionada como Base64 textual.
- O GitHub Actions reconstrói o JPG válido antes de executar testes e build.
- Os binários corrompidos deixam de ficar armazenados diretamente no repositório.
- O mesmo JPG reconstruído alimenta a Home e o ícone do launcher.
- A imagem continua sendo a arte original FioLab Matrizes, sem redesenho.


## 0.21.3

### Correção crítica de imagem
- Substitui a logo interna corrompida por uma cópia válida da arte original FioLab Matrizes.
- Substitui o recurso de launcher corrompido pela mesma arte original redimensionada.
- Remove o PNG inválido que fazia o Android usar o ícone verde genérico.
- A Home deixa de tentar decodificar o arquivo gráfico corrompido que podia causar fechamento imediato.
- Nenhuma alteração visual foi feita na marca; apenas redimensionamento/compressão para Android.


## 0.21.2

### Correção de inicialização
- Minha Conta/Supabase foi isolada da tela inicial.
- O cliente Supabase só é carregado quando o usuário abre Minha Conta.
- Ktor alinhado para 3.2.2 com a linha Supabase 3.2.x.
- A Home não depende mais do SDK de autenticação para iniciar.

### Launcher
- Ícone principal passa a usar recurso mipmap próprio.
- A arte FioLab Matrizes fornecida pelo projeto continua sendo a identidade do app.
- Corrige o fallback visual para o ícone Android genérico em launchers/instaladores compatíveis.


## 0.21.1

### Identidade visual
- A imagem FioLab Matrizes enviada pelo projeto passa a ser o ícone do launcher.
- A mesma arte aparece no cabeçalho da Home.
- O ícone antigo com a letra "F" deixa de ser usado como identidade principal.
- A arte foi apenas dimensionada para uso no Android; não foi redesenhada.


## 0.21.0

### Minha Conta
- Nova área "Minha Conta" na Home.
- Cadastro por nome, e-mail e senha.
- Login por e-mail e senha.
- Sessão persistida pelo cliente Supabase no Android.
- Perfil com nome editável.
- Logout.
- Conta não envia automaticamente "Minhas Matrizes" para a nuvem.

### Assinatura preparada
- Cartão "Minha assinatura" dentro da conta.
- Exibe plano atual.
- Exibe status: ativa, teste, pendente, cancelada ou expirada.
- Exibe validade/próxima renovação quando existir.
- Conta nova começa no plano gratuito.
- O aplicativo tem apenas leitura da assinatura; não consegue se autoatribuir plano pago.

### Backend
- Tabelas próprias public.fiolab_profiles e public.fiolab_subscriptions.
- RLS habilitado.
- Usuário só lê/edita o próprio perfil.
- Usuário só lê a própria assinatura.
- Nenhuma permissão de escrita de assinatura para o cliente Android.
- Perfil/assinatura padrão são criados para cadastros identificados com app_slug=fiolab.
- Estrutura versionada em supabase/fiolab_account_schema.sql.

### Dependências
- supabase-kt 3.2.3.
- Auth e PostgREST.
- Ktor Android 3.3.0.


## 0.20.0

### Backup e restauração
- Backup de todas as matrizes salvas em um único arquivo portátil.
- Botão Backup em Minhas Matrizes.
- Botão Restaurar em Minhas Matrizes.
- O arquivo pode ser salvo em Downloads, cartão/armazenamento disponível ou pendrive OTG pelo seletor do Android.
- O backup pode ser levado para outro aparelho e restaurado.
- Projetos restaurados voltam para a biblioteca local e continuam exportáveis em DST/PES/JEF.
- Matrizes com o mesmo identificador são atualizadas durante a restauração em vez de duplicadas.

### Segurança do backup
- Formato de backup versionado.
- Manifesto interno identifica backups válidos do FioLab.
- Cada projeto é validado pelo codec antes de entrar no backup.
- Restauração rejeita backup incompleto, corrompido ou com versão não suportada.
- Proteções de quantidade e tamanho evitam arquivos ZIP malformados/excessivos.
- Caminhos suspeitos dentro do ZIP são rejeitados.

### Testes
- Round-trip de múltiplas matrizes em um backup.
- Backup vazio válido no codec.
- Arquivo arbitrário é rejeitado.
- Projeto corrompido não entra no backup.


## 0.19.0

### Minhas Matrizes
- Nova biblioteca local "Minhas Matrizes".
- Salva projetos dentro do armazenamento privado do FioLab.
- Não exige conta, internet ou serviço externo.
- Botão "Salvar em Minhas Matrizes" no visualizador.
- Reabrir projeto salvo.
- Excluir projeto salvo.
- Enviar uma matriz salva diretamente para a máquina.
- Salvar novamente a mesma matriz atualiza sua cópia local em vez de gerar duplicatas.

### Projeto FioLab
- Formato interno versionado para preservar pontadas.
- Preserva comandos STITCH/JUMP/TRIM/COLOR_CHANGE/END.
- Preserva paleta de cores.
- Preserva bastidor.
- Preserva perfil de tecido.
- Preserva dados de acabamento automático.
- Ao reabrir, a matriz volta como projeto editável e pode ser exportada em DST/PES/JEF.

### Qualidade
- Codec interno testado em round-trip.
- Projeto corrompido é rejeitado em vez de ser aberto parcialmente.
- A lista ignora arquivos locais inválidos sem derrubar toda a biblioteca.


## 0.18.0

### Criar desenho/logo
- Novo fluxo "Criar desenho/logo" na Home.
- Importação de SVG simples pelo seletor de arquivos do Android.
- Desenho à mão livre diretamente na tela.
- Cada traço vira um caminho real de bordado.
- Controle de largura em milímetros.
- Cor da linha.
- Ponto corrido.
- Satin aplicado sobre o traço.
- Escolha de bastidor.
- Exportação DST, PES e JEF.
- Integração com Viewer, Simulador e Enviar para máquina.

### SVG inicial
- path com M/L/H/V/C/S/Q/T/Z.
- line.
- polyline.
- polygon.
- rect.
- circle.
- ellipse.
- Curvas cúbicas e quadráticas são amostradas em pontos antes da geração da matriz.
- Arcos SVG (A/a) e transforms complexos ainda não são aceitos.
- A v0.18 trabalha o contorno/traço; não faz preenchimento automático de áreas complexas.

### Qualidade
- SVG limitado a 2 MB nesta versão.
- Desenho é normalizado para tamanho físico em milímetros.
- Acabamento de máquina continua automático por baixo.
- Testes cobrem parsing, ponto corrido, Satin e exportação DST/PES/JEF.


## 0.17.0

### Foco do produto
- O FioLab permanece um app leve para criar fontes, nomes, monogramas e matrizes.
- Não tenta substituir softwares completos de digitalização profissional.
- O acabamento técnico continua automático por baixo, sem ocupar a interface principal.

### Enviar para a máquina
- Nova tela dedicada "Enviar para a máquina".
- Escolha rápida entre DST, PES e JEF.
- Fluxo Pendrive OTG usando o seletor de armazenamento do Android.
- O usuário escolhe o pendrive USB como destino e salva o arquivo diretamente nele.
- Fluxo Wi-Fi/app gera o arquivo e abre os destinos compatíveis instalados no Android.
- Compatibilidade Wi-Fi direta depende do protocolo ou aplicativo suportado pela bordadeira.

### Interface simplificada
- Botão "Enviar para máquina" na Home.
- Botão principal "Enviar para máquina" no visualizador.
- Controles avançados de tie-in/tie-off/cortes deixam de ocupar Criar Nome e Monograma.
- O acabamento automático continua aplicado com os padrões seguros da v0.16.


## 0.16.0

### Acabamento de Máquina
- Tie-in automático no início de cada bloco costurado.
- Tie-off automático antes de troca de cor, corte e final do bordado.
- Comprimento curto de arremate para reduzir pontas soltas.
- Corte automático antes de saltos acima do limite configurado.
- Limite padrão de corte em 8 mm, editável pelo usuário.
- Otimização conservadora de deslocamentos e comandos redundantes.
- A otimização não reordena blocos nem cores.

### Controle de Qualidade
- Análise de pontos longos.
- Alerta padrão para pontos acima de 7 mm.
- Análise de saltos longos.
- Alerta padrão para saltos acima de 12 mm.
- Exibição dos alertas diretamente no visualizador da matriz.
- Matrizes criadas guardam informação de acabamento utilizado.

### Criar Nome e Monograma
- Controles para ativar/desativar tie-in.
- Controles para ativar/desativar tie-off.
- Corte automático de saltos configurável.
- Limite de corte ajustável.
- Otimização segura de deslocamentos configurável.
- Acabamento aplicado depois de Satin, curva, multicor e posicionamento final.

### Segurança da Sequência
- COLOR_CHANGE é preservado.
- A ordem dos blocos de cor não é alterada.
- A geometria visual da matriz não é reorganizada pelo acabamento.


## 0.15.0

### Multicor real
- Cor individual por letra no Criar Nome.
- Cor individual por inicial no Criar Monograma.
- Cores iguais consecutivas permanecem no mesmo bloco.
- Quando a cor muda, o FioLab insere COLOR_CHANGE real na sequência.
- Se uma cor voltar depois de outro bloco, uma nova troca é criada.
- Cada ponto recebe o índice de cor correspondente ao bloco.
- Paleta da sequência preservada no modelo da matriz.

### Simulação
- A Simulação de Máquina pausa automaticamente em COLOR_CHANGE.
- O app informa o bloco seguinte.
- Botão Continuar retoma a costura após a troca de linha.
- Marcador e pontadas usam a cor real do bloco.

### Exportação
- PES e JEF recebem a sequência de fios/paleta.
- DST preserva os comandos de troca de cor, embora o formato não carregue uma paleta RGB completa.
- Testes multicor para nomes e monogramas.
- Exportação multicor validada em DST, PES e JEF.


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
