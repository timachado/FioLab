# Changelog

## 0.5.0

### Adicionado
- Conversão real entre DST, PES e JEF.
- Tela de conversão com seleção de formato de destino.
- Converter e salvar pelo seletor nativo do Android.
- Converter e compartilhar por aplicativos compatíveis.
- Preservação da paleta de linhas quando PES/JEF fornecem cores.
- Cores padrão quando o formato de origem não contém paleta, como DST.
- Testes de round-trip: gera DST/PES/JEF e reabre o arquivo convertido.
- Correção da orientação vertical normalizada de JEF/PES.
- Original continua protegido; conversão sempre gera novo arquivo.

## 0.4.0

### Adicionado
- Leitura real de matrizes JEF.
- Leitura real de matrizes PES.
- Testes com arquivos binários reais JEF/PES.
- Camada de compatibilidade EmbroideryIO.

## 0.3.0

### Adicionado
- Salvar cópia no Android.
- Destino em memória, SD e USB/OTG quando disponível.
- Compartilhamento seguro via FileProvider.

## 0.2.0

### Adicionado
- Simulador progressivo de pontadas.
- Play, pausa, avanço, retrocesso e velocidade.
- Estimativa de consumo de linha.
