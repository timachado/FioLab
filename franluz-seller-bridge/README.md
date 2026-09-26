# FranLuz Seller Bridge 2.0.0

Backend do aplicativo Android nativo FranLuz Seller.

## Objetivo

Separar totalmente a interface do vendedor do tema/site FranLuz Home Decor. O app consome JSON e não depende de HTML, cookies de WebView, seletores CSS ou JavaScript injetado.

## Segurança

- Login validado pelo próprio WordPress.
- Acesso limitado a administrador, shop_manager, seller/vendedor/vendor ou contas com capacidades WooCommerce/produtos.
- Access token aleatório de 12h.
- Refresh token aleatório de 30 dias, com rotação.
- Somente hashes dos tokens ficam no banco.
- Rate-limit de login.
- HTTPS obrigatório.
- Nenhuma Consumer Key/Secret do WooCommerce é enviada ao Android.
- Cabeçalhos aceitos: Authorization: Bearer e X-FranLuz-Token (fallback para hospedagens que removem Authorization).

## Endpoints iniciais

- GET /health
- POST /auth/login
- POST /auth/refresh
- POST /auth/logout
- GET /me
- GET /dashboard
- GET/POST /products
- GET/PATCH /products/{id}
- GET /orders
- GET/PATCH /orders/{id}
- GET /stock/low
- POST /media

Base: /wp-json/franluz-seller/v1/
