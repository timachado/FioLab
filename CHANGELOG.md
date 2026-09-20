# Changelog

## 0.4.0

### Adicionado
- Leitura real de matrizes Janome/Elna JEF.
- Leitura real de matrizes Brother PES.
- Nova camada de compatibilidade baseada em EmbroideryIO para formatos complexos.
- Normalização de pontos JEF/PES para o modelo interno do FioLab.
- Tratamento de STITCH, JUMP, TRIM, STOP, COLOR_CHANGE, SEQUIN e END.
- Testes automatizados com arquivos JEF e PES binários reais.
- Identificação do formato na Home e no visualizador.
- Avisos de licença/atribuição de dependências MIT.

### Preservado
- Parser DST próprio do FioLab.
- Visualizador e zoom.
- Simulador progressivo.
- Salvar cópia em armazenamento/OTG.
- Compartilhamento Android.
- Arquivo original protegido.

## 0.3.0

### Adicionado
- Salvar uma cópia da matriz pelo seletor nativo do Android.
- Escolha de destino compatível com memória interna, cartão SD e USB/pendrive OTG quando disponível no Android.
- Compartilhamento da matriz por aplicativos instalados.
- FileProvider seguro para compartilhamento sem expor caminhos internos.
- Sanitização do nome do arquivo.

## 0.2.0

### Adicionado
- Simulador progressivo de pontadas DST.
- Play e pausa.
- Avanço e retrocesso.
- Controle de velocidade.
- Estimativa de consumo de linha.
