# Changelog

## 0.35.0

### Simulação alinhada ao comportamento de referência
- Corrigida a escala física da simulação: quando há bastidor selecionado, o tamanho da matriz passa a ser calculado diretamente pelo tamanho real do bastidor, sem o fator artificial de ampliação anterior.
- O contorno do bastidor e o desenho agora usam a mesma geometria e a mesma escala, evitando discrepância visual entre matriz e área de referência.
- A matriz ainda não bordada fica mais suave durante a simulação, funcionando como guia visual; os pontos já executados permanecem fortes e progressivos.
- O fio ativo no modo Realista foi afinado para que o preenchimento resulte da densidade real dos pontos, e não da espessura artificial da linha desenhada.
- O contorno da área de simulação foi aproximado do estilo neutro da referência.
- Incluídos testes de regressão para garantir que bastidores diferentes mantenham escalas coerentes.
- Leitura PES/DST/JEF, geração de matrizes, TTF/OTF, Satin e sequência original de pontos permanecem inalterados.

## 0.34.0

### Simulação realista de bordado
- O modo Realista passa a renderizar cada ponto como fio fino direcional, com sombra e brilho acompanhando o ângulo real do ponto.
- A simulação mantém o desenho ainda não bordado visível como referência, mas com acabamento mais próximo de uma matriz real em tecido.
- O fundo usa tecido claro com grade técnica mais fina e o bastidor recebe contorno tracejado dourado.
- O enquadramento da simulação foi ampliado para aproveitar melhor a área útil.
- O visualizador da matriz agora reserva mais espaço vertical para o desenho; o cartão de informações passa a ter rolagem própria.
- O modo Realista do visualizador usa o mesmo conceito visual de tecido, grade e fio da simulação.
- Geração de matrizes, leitura PES/DST/JEF, TTF/OTF, Satin, cores e sequência de pontos permanecem inalterados.

## 0.33.0

### Biblioteca de fontes persistente
- A Home passa a exibir **Biblioteca de fontes** com acesso direto ao gerenciamento de TTF/OTF.
- A ação principal agora é **Importar e salvar fonte TTF/OTF**, deixando explícito que a fonte fica armazenada no FioLab.
- Fontes salvas continuam disponíveis em **Criar Nome** até serem excluídas pelo usuário.
- Importações repetidas são reconhecidas e não criam cópias duplicadas.
- A seção de Criar Nome passa a usar o rótulo **Fontes salvas** e orienta o usuário a usar a Biblioteca de fontes na Home.
- O motor de matrizes, Satin, simulação e demais recursos da 0.32.0 permanecem inalterados.

## 0.32.0

### Exibição sem reduzir a área da matriz
- O botão Exibição volta para o cabeçalho do visualizador, como no Editor, removendo a faixa larga que reduzia a prévia.
- Criar Nome ganha controles de Exibição dentro da aba Mais: Sólida, Pontos e Realista.
- A aba Mais também permite mostrar ou ocultar o bastidor somente na prévia de Criar Nome.
- A escolha de Exibição feita em Criar Nome é mantida ao abrir a matriz criada ou iniciar a simulação.
- O motor de geração de matrizes, fontes TTF/OTF, Satin e demais ajustes da 0.31.0 foram preservados.

## 0.31.0

### Exibição preservada na simulação
- O seletor de Exibição saiu do canto apertado do cabeçalho e ganhou um botão próprio, largo e visível, indicando o modo atual.
- Sólida, Pontos e Realista passam a ser transportados da visualização para a simulação sem voltar silenciosamente ao padrão.
- O bastidor de referência e a opção Traços de conexão também seguem para a simulação.
- Ao voltar da simulação, as configurações de exibição usadas naquela matriz permanecem no fluxo atual.
- A simulação mantém agulha, progresso, velocidade, pausa, troca de linha e guia vetorial já existentes.
- O motor de pontos, geração de matrizes, fontes TTF/OTF e demais fluxos da 0.30.0 não foram alterados.

## 0.30.0

### Novo motor de texto TTF/OTF
- O caminho Satin experimental anterior deixa de ser usado para fontes importadas.
- Reimplementado o pipeline funcional observado no app de referência: extração de contorno por glifo, polygonize, scan spans nos eixos X/Y, escolha do eixo com menor largura média, BuildColumns e SplitWideColumn.
- A altura passa a ser resolvida por cap-height da fonte, em vez de escalar a caixa inteira da palavra.
- Densidade Satin padrão passa a 0,4 mm.
- A largura máxima Satin para divisão de colunas passa a 7,0 mm; a largura real continua vindo do desenho da fonte.
- Pull compensation padrão permanece 0,2 mm.
- O espaçamento base passa a ser proporcional à altura da letra (4%), com ajuste manual adicional.
- Cada glifo é digitalizado em suas próprias colunas, preservando melhor contraformas e ligações do desenho.
- Underlay central faz ida e volta pelos centros das linhas da coluna.
- Cada coluna recebe lock de 0,6 mm no início e no fim.
- Viagens acima de 5 mm geram TRIM; jumps e stitches são segmentados em no máximo 7 mm.
- O guia vetorial original da fonte continua independente dos pontos e permanece disponível na simulação.
- A implementação Kotlin é própria; o APK de referência foi usado para reproduzir o comportamento e os parâmetros, não para incorporar código ou assets.

## 0.29.0

### Espessura fiel em fontes importadas
- Corrigido o Ponto Corrido de TTF/OTF, que antes costurava as duas bordas do contorno e fazia fontes finas parecerem muito mais grossas.
- Ponto Corrido passa a usar um único eixo central suavizado do traço.
- Ponto Feijão, Ponto Corrido Triplo e Ponto de Motivo também passam a usar esse mesmo eixo central como base.
- O contorno vetorial original continua preservado separadamente como guia da fonte.
- A prévia de Criar Nome passa a usar o modo Sólida, sem sombra ou volume artificial.
- O modo Sólida foi afinado para leitura mais próxima do desenho real da fonte.
- Satin continua usando a largura real da forma vetorial, pois nesse modo a espessura faz parte da própria técnica de bordado.
- O objetivo é aproximar a visualização e a sequência de pontos do comportamento observado em softwares de bordado como PE-Design, sem duplicar as bordas da TTF.

## 0.28.0

### Pontos especiais
- A área de Underlay exposta em Criar Nome foi substituída por tipos de ponto especiais.
- Ponto Feijão: repete cada pequeno trecho em ida/volta/ida para engrossar e marcar o traçado sem depender de Satin.
- Ponto Corrido Triplo: percorre o caminho completo três vezes, frente/volta/frente, aumentando durabilidade e visibilidade.
- Ponto de Motivo: aplica um padrão decorativo repetido ao longo do traçado, com deslocamentos laterais controlados.
- Os três modos funcionam tanto com fontes FioLab quanto com TTF/OTF importadas.
- Ao selecionar um ponto especial, o motor usa automaticamente o traçado corrido como base; ao selecionar Satin ou Ponto corrido na aba Tamanho, o modo especial é limpo.
- Underlay e short stitches continuam disponíveis internamente para o Satin conforme o perfil de tecido, sem poluir a interface.
- Mantido o guia vetorial reforçado da simulação para comparar a fonte original com o bordado gerado.
- Adicionados testes unitários específicos para Feijão, Corrido Triplo e Motivo.

## 0.27.0

### Guia vetorial da fonte na simulação
- O nome de fundo deixa de ser apenas uma cópia transparente da sequência de pontos.
- Fontes TTF/OTF passam a carregar junto da matriz um guia vetorial derivado diretamente do contorno original da fonte.
- O simulador desenha esse guia como silhueta translúcida rosé com contorno mais definido.
- A linha realmente costurada continua por cima, permitindo comparar visualmente cobertura, direção e desvios do Satin.
- O guia usa preenchimento EvenOdd para preservar contraformas e furos internos das letras.
- Projetos FioLab passam ao formato interno v2 para persistir o guia vetorial; projetos v1 continuam sendo aceitos normalmente.
- Matrizes importadas sem guia vetorial continuam usando a prévia fantasma anterior como fallback.

## 0.26.0

### Satin orientado pelo traço
- Corrigida a limitação restante da 0.25.0, que preservava melhor o contorno mas ainda mantinha muitas colunas Satin em orientação horizontal.
- O contorno vetorial da TTF/OTF continua sendo a fonte da forma final.
- Uma máscara de alta resolução é usada apenas para obter um eixo central suavizado e estimar a direção local do traço.
- O eixo não define mais o desenho da letra; ele serve somente como campo de orientação.
- Cada amostra calcula a tangente local do traço e lança uma normal perpendicular para encontrar as duas bordas reais da letra.
- As pontadas Satin passam a girar junto com hastes, curvas e ligações da fonte.
- O rastreamento do eixo atravessa junções escolhendo a continuação de menor mudança angular, reduzindo fragmentação em letras cursivas.
- Pequenos ramos residuais são descartados antes da geração dos pontos.
- A largura de cada lado é suavizada ao longo do caminho para evitar picos.
- JUMP/TRIM continuam fora da visualização costurada e a simulação continua usando a sequência real de pontos.
- Validado visualmente com a fonte Alliby usada no teste de "Maria", sem regras específicas para essa família.

## 0.25.0

### Novo digitalizador Satin para fontes
- Removido do caminho principal o esqueleto rasterizado que quebrava curvas, junções e letras cursivas.
- Implementado gerador baseado em SatinRow e SatinColumn, seguindo a arquitetura observada no comportamento do Mão Design sem copiar código proprietário.
- Cada linha interna do glifo é calculada diretamente pelas interseções com o contorno vetorial da TTF/OTF.
- No texto reto normal, a palavra inteira é analisada como uma única forma antes da criação das colunas, preservando melhor ligações e sobreposições de fontes cursivas.
- As faixas são agrupadas verticalmente por sobreposição e proximidade, formando colunas contínuas de bordado.
- Mudanças de topologia criam novas colunas em vez de fragmentar a letra em ramos serrilhados.
- As bordas de cada coluna recebem suavização local antes da geração dos pontos.
- As colunas são roteadas pela extremidade mais próxima para reduzir saltos e manter uma ordem natural.
- Underlay central é calculado por coluna.
- Pontos Satin largos continuam sendo divididos em comprimentos seguros.
- A simulação continua reproduzindo a sequência real de pontos e não desenha JUMP/TRIM como costura.

## 0.24.0

### Satin de fontes e simulação
- Analisado o comportamento do Mão Design a partir do vídeo e do APK fornecidos, usando apenas a arquitetura/comportamento como referência.
- O FioLab passa a separar claramente digitalização da fonte e renderização da simulação.
- O modo Satin de TTF/OTF não possui mais fallback silencioso para preenchimento horizontal.
- Glifos simples usam colunas Satin normais; geometrias complexas usam colunas Satin conservadoras, mantendo o mesmo princípio de costura.
- Trechos do eixo são ordenados por proximidade para reduzir deslocamentos e manter uma sequência natural.
- Pontos Satin longos são divididos automaticamente em segmentos seguros sem mudar para varredura horizontal.
- A sequência passa por reparo para remover repetições degeneradas e limitar comprimentos excessivos.
- Quando não existe eixo utilizável, o fallback é contorno seguro em ponto corrido, nunca scanline horizontal disfarçado de Satin.
- A simulação reproduz exatamente a sequência de pontos gerada e ignora deslocamentos sem costura na renderização.
- Percentual, slider e progresso passam a usar pontos realmente costurados, não JUMP/TRIM.
- Renderização da linha costurada recebeu sombra, corpo e brilho para aspecto mais próximo de fio real.

## 0.23.0

### Motor adaptativo TTF/OTF
- Removida a abordagem de corrigir comportamento para uma família de fonte específica.
- Cada glifo importado passa a ser analisado geometricamente antes da digitalização.
- O motor mede área, comprimento do esqueleto, ramificações, largura estimada e proporção do desenho.
- Fontes finas, cursivas e de traço contínuo usam Satin pelo eixo quando a geometria é adequada.
- Fontes muito grossas, display, decorativas ou com ramificações excessivas usam preenchimento de área como fallback seguro.
- O resultado passa por preflight automático antes de ser aceito.
- O preflight rejeita trajetórias com pontos excessivamente longos, repetições degeneradas e proporção anormal de saltos.
- Se o Satin por eixo falhar no preflight, o gerador refaz o glifo automaticamente com a técnica segura.
- A decisão não usa nome, fabricante ou família da fonte; depende apenas da geometria real do glifo.
- Adicionados testes de regressão para perfis fino/cursivo, grosso/display, decorativo, degenerado e trajetórias inválidas.
- Adicionado painel de Configurações de Exibição inspirado no fluxo de referência.
- Modos disponíveis: Sólida, Pontos e Realista.
- Bastidor de referência pode ser trocado sem alterar a matriz.
- Traços de conexão podem ser exibidos/ocultados separadamente.
- Os mesmos modos de exibição ficam disponíveis no Visualizador e no Editor.

## 0.22.4

### Fidelidade de fontes importadas
- Ajustado o Satin por eixo para fontes cursivas e caligráficas como Alliby.
- O eixo central extraído da letra passa por suavização antes do cálculo das pontadas.
- Ramificações curtas do esqueleto são descartadas para evitar dentes e desvios falsos.
- A direção do Satin usa uma janela maior do eixo para reduzir mudanças bruscas entre pixels.
- A largura das duas bordas é suavizada ao longo do traço para reduzir picos.
- A rasterização interna passa de 1,5 para 1,0 unidade por pixel, melhorando a precisão de curvas.
- Mantidos o reconhecimento de matrizes por conteúdo, matriz ativa persistente e o Material 3 Expressive.

## 0.22.3

### Matrizes e simulação
- O carregador deixa de depender apenas da extensão mostrada pelo Android.
- PES e DST passam a ser reconhecidos por assinatura do conteúdo; JEF também é tentado quando o nome vem sem extensão.
- A matriz aberta/criada/editada passa a ser persistida como matriz ativa para continuar disponível na Home e no Simulador.
- Fontes TTF/OTF em Satin deixam de usar preenchimento por varredura horizontal.
- Novo gerador rasteriza o glifo, extrai o eixo central do traço e alterna as pontadas entre as duas bordas ao longo desse eixo.
- A sequência avança pelas hastes e curvas da letra, aproximando o comportamento visual do vídeo de referência.
- Mantidos TRIM/JUMP sem linhas falsas na simulação e a interface Material 3 Expressive.

## 0.22.2

### Fontes TTF/OTF e simulação
- Corrigida a ordem de geração das fontes importadas: o texto deixa de ser preenchido por varredura horizontal da palavra inteira.
- Cada caractere passa a ser convertido e costurado em sequência de leitura, mantendo seus contornos e furos internos agrupados.
- O espaçamento configurado passa a participar do posicionamento das letras importadas na geração direta.
- Deslocamentos JUMP deixam de ser desenhados como linhas no simulador, pois são movimentos sem costura.
- TRIM, STOP, COLOR_CHANGE e END agora interrompem corretamente o traço visual antes do próximo trecho costurado.
- Mantidos Material 3 Expressive, Material Symbols, importação TTF/OTF e formatos de exportação existentes.

## 0.22.1

### Material 3 Expressive
- Home reformulada com hierarquia visual mais clara e ação principal "Criar nome" em destaque.
- Cards secundários reorganizados com shapes maiores, elevação tonal e estados habilitado/desabilitado mais claros.
- Barra inferior mantém cinco destinos e passa a usar ícones vetoriais reais em vez de caracteres de texto.
- Ícones importados do conjunto oficial Material Symbols Rounded do Google para Início, Criar, Matrizes, Fontes, Conta, Abrir, Simular, Editar, Converter e Enviar.
- Tema FioLab passa a definir escala própria de shapes e tipografia Material 3, preservando a identidade dourado + azul-escuro.
- Mantida a base Compose compatível com o SDK 36 atual do projeto, aplicando a linguagem visual M3 Expressive sem quebrar a pipeline.

## 0.22.0

### Criar Nome
- Interface reorganizada para reduzir a sensação de tela carregada.
- Prévia permanece grande e atualiza em tempo real.
- Controles separados em abas curtas: Texto, Fonte, Tamanho, Cor e Mais.
- Texto concentra digitação e formato reto/arco.
- Fonte concentra famílias FioLab e TTF/OTF importadas.
- Tamanho concentra altura, espaçamento e tipo/densidade de ponto.
- Cor concentra a paleta de linha.
- Bastidor, tecido, formato e ajustes técnicos ficam em Mais.
- Geração TTF/OTF e o motor de pontos da 0.21.8 são preservados.

### Simulação
- Bastidor passa a ocupar a maior parte da tela.
- Matriz completa fica visível como desenho fantasma claro ao fundo.
- Pontadas concluídas aparecem fortes e progressivas sobre a matriz fantasma.
- Marcador atual da costura passa a ser um ponto discreto.
- Percentual e dimensões ficam sobre o bastidor.
- Velocidades 1x, 2x e 4x permanecem no cabeçalho.
- Painel inferior compacto mostra linha atual, pontos, tempo restante, progresso e controles de reprodução.
- Removidos os cartões grandes de estatísticas e o texto explicativo que ocupavam espaço da simulação.

## 0.21.8

### Correção TTF/OTF
- Corrigida a causa do texto importado aparecer como bolhas/traços no Criar Nome.
- O modo Satin de fontes TTF/OTF deixa de tratar o contorno externo como se fosse uma linha central.
- O gerador passa a preencher a área interna real dos glifos por varredura, preservando furos e formas internas pelo preenchimento par/ímpar.
- A escala da fonte passa a usar a altura visual real do contorno, em vez da altura tipográfica total do arquivo, evitando nomes muito pequenos.
- Ponto corrido continua seguindo o contorno vetorial.
- Fontes manuscritas e ligadas continuam sendo processadas como palavra inteira quando não há ajustes individuais por letra.
- Caracteres ausentes continuam sendo detectados explicitamente, sem substituição silenciosa por ponto de interrogação.

## 0.21.7

### Fontes importadas
- Fontes TTF/OTF passam a usar o contorno vetorial da palavra inteira no fluxo normal de Criar Nome, preservando melhor a forma original, kerning e fontes manuscritas/ligadas.
- Caracteres ausentes deixam de cair silenciosamente em glifos de substituição; o app tenta uma versão sem diacrítico e, se ainda não existir, mostra um erro claro.
- Importador reconhece TTF/OTF pelo cabeçalho real do arquivo e aceita fontes válidas mesmo quando o Android entrega o documento sem extensão no nome.
- Ajustes individuais por letra e texto curvo continuam disponíveis usando o modo por glifo quando necessário.

### Home
- Removidos os cards Monograma e Desenho/logo da grade principal.
- Adicionado card Fontes no lugar, com acesso direto às fontes nativas e importadas.
- As implementações de Monograma e Desenho permanecem preservadas no código para não perder trabalho existente.

### Simulador
- Matrizes pequenas deixam de ser reduzidas ao tamanho físico completo do bastidor na prévia.
- Zoom automático enquadra a área real do bordado com margem visual e limite de ampliação.
- Dimensão do bastidor e sequência real de pontos continuam preservadas.

## 0.21.6

### Navegação e Home
- Home reorganizada em grade de duas colunas para reduzir rolagem e deixar as ferramentas mais fáceis de localizar.
- Barra inferior fixa nas áreas principais com Início, Criar, Matrizes, Fontes e Conta.
- Minha Conta deixa de ocupar um card grande na Home e passa a ficar sempre acessível pela navegação inferior.
- Minhas Matrizes e Biblioteca de Fontes também ficam disponíveis na barra inferior.
- Botão/gesto Voltar do Android agora retorna à tela lógica anterior em vez de encerrar o aplicativo quando o usuário está dentro de uma função.
- Na Home, o Voltar mantém o comportamento normal do Android e pode sair do aplicativo.
- Mantidos importação TTF/OTF, criação de nomes/monogramas, desenho, simulador, editor, conversor e envio para máquina.

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
