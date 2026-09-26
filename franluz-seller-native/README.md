# FranLuz Seller 2.0 Native

Nova base do aplicativo de vendedor da FranLuz Home Decor.

## Arquitetura

Android nativo (Kotlin + Jetpack Compose)
→ HTTPS/JSON
→ FranLuz Seller Bridge (plugin WordPress)
→ WooCommerce

O app 2.0 não utiliza WebView e não depende do HTML ou do tema da loja.

## Primeira base funcional

- Login nativo.
- Access token + refresh token.
- Tokens criptografados pelo Android Keystore.
- Dashboard.
- Pedidos.
- Produtos.
- Estoque baixo.
- Conta/logout.
- Estados de loading, erro e vazio.
- API base configurada por BuildConfig.

## Backend

Instale o plugin em ../franluz-seller-bridge antes de testar login/dados reais.

## Identidade

Application ID: com.franluz.seller
Versão: 2.0.0
Min SDK: 26
Target/Compile SDK: 36
