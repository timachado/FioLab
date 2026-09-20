# Changelog

## 0.3.0

### Adicionado
- Salvar uma cópia da matriz pelo seletor nativo do Android.
- Escolha de destino compatível com memória interna, cartão SD e USB/pendrive OTG quando disponível no Android.
- Compartilhamento da matriz por aplicativos instalados, incluindo mensageiros e e-mail.
- FileProvider seguro para compartilhamento sem expor caminhos internos.
- Sanitização do nome do arquivo antes de criar cópias temporárias.
- Testes unitários para nomes de arquivo seguros.

### Preservado
- Leitura real DST.
- Visualizador com zoom e movimentação.
- Simulador progressivo.
- Informações de pontos, dimensões e blocos.
- Estimativa de linha.
- Arquivo original sem alteração.

## 0.2.0

### Adicionado
- Simulador progressivo de pontadas DST.
- Play e pausa.
- Avanço e retrocesso de 50 eventos.
- Controle de velocidade de 0,5× a 5×.
- Barra de progresso e percentual concluído.
- Bloco de cor atual.
- Estimativa inicial de consumo de linha.
- Pipeline GitHub Actions para testes e geração do APK.

### Preservado
- Leitor DST da 0.1.
- Visualizador, zoom e movimentação.
- Arquivo original somente leitura.
