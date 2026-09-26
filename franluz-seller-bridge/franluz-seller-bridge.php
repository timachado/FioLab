<?php
/**
 * Plugin Name: FranLuz Seller Bridge
 * Description: API segura para o aplicativo Android nativo FranLuz Seller.
 * Version: 2.0.0
 * Author: T.I. Machado — Soluções em Tecnologia
 * Requires at least: 6.4
 * Requires PHP: 8.0
 * WC requires at least: 8.0
 */

defined('ABSPATH') || exit;

final class FranLuz_Seller_Bridge {
    const VERSION = '2.0.0';
    const REST_NS = 'franluz-seller/v1';
    const ACCESS_TTL = 43200;       // 12 horas
    const REFRESH_TTL = 2592000;    // 30 dias

    private string $table;
    private ?int $current_token_id = null;

    public function __construct() {
        global $wpdb;
        $this->table = $wpdb->prefix . 'franluz_seller_tokens';

        add_action('rest_api_init', [$this, 'register_routes']);
        add_action('admin_menu', [$this, 'register_admin_page']);
    }

    public static function activate(): void {
        global $wpdb;
        $table = $wpdb->prefix . 'franluz_seller_tokens';
        $charset = $wpdb->get_charset_collate();

        require_once ABSPATH . 'wp-admin/includes/upgrade.php';

        dbDelta("CREATE TABLE {$table} (
            id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
            user_id BIGINT UNSIGNED NOT NULL,
            access_hash CHAR(64) NOT NULL,
            refresh_hash CHAR(64) NOT NULL,
            device_name VARCHAR(190) NOT NULL DEFAULT '',
            access_expires_at DATETIME NOT NULL,
            refresh_expires_at DATETIME NOT NULL,
            created_at DATETIME NOT NULL,
            last_used_at DATETIME NULL,
            PRIMARY KEY (id),
            UNIQUE KEY access_hash (access_hash),
            UNIQUE KEY refresh_hash (refresh_hash),
            KEY user_id (user_id),
            KEY refresh_expires_at (refresh_expires_at)
        ) {$charset};");
    }

    public function register_routes(): void {
        register_rest_route(self::REST_NS, '/health', [
            'methods' => 'GET',
            'callback' => [$this, 'health'],
            'permission_callback' => '__return_true',
        ]);

        register_rest_route(self::REST_NS, '/auth/login', [
            'methods' => 'POST',
            'callback' => [$this, 'login'],
            'permission_callback' => '__return_true',
        ]);

        register_rest_route(self::REST_NS, '/auth/refresh', [
            'methods' => 'POST',
            'callback' => [$this, 'refresh'],
            'permission_callback' => '__return_true',
        ]);

        register_rest_route(self::REST_NS, '/auth/logout', [
            'methods' => 'POST',
            'callback' => [$this, 'logout'],
            'permission_callback' => [$this, 'permission'],
        ]);

        register_rest_route(self::REST_NS, '/me', [
            'methods' => 'GET',
            'callback' => [$this, 'me'],
            'permission_callback' => [$this, 'permission'],
        ]);

        register_rest_route(self::REST_NS, '/dashboard', [
            'methods' => 'GET',
            'callback' => [$this, 'dashboard'],
            'permission_callback' => [$this, 'permission'],
        ]);

        register_rest_route(self::REST_NS, '/products', [
            [
                'methods' => 'GET',
                'callback' => [$this, 'products'],
                'permission_callback' => [$this, 'permission'],
            ],
            [
                'methods' => 'POST',
                'callback' => [$this, 'create_product'],
                'permission_callback' => [$this, 'permission'],
            ],
        ]);

        register_rest_route(self::REST_NS, '/products/(?P<id>\d+)', [
            [
                'methods' => 'GET',
                'callback' => [$this, 'product'],
                'permission_callback' => [$this, 'permission'],
            ],
            [
                'methods' => ['POST', 'PUT', 'PATCH'],
                'callback' => [$this, 'update_product'],
                'permission_callback' => [$this, 'permission'],
            ],
        ]);

        register_rest_route(self::REST_NS, '/orders', [
            'methods' => 'GET',
            'callback' => [$this, 'orders'],
            'permission_callback' => [$this, 'permission'],
        ]);

        register_rest_route(self::REST_NS, '/orders/(?P<id>\d+)', [
            [
                'methods' => 'GET',
                'callback' => [$this, 'order'],
                'permission_callback' => [$this, 'permission'],
            ],
            [
                'methods' => ['POST', 'PUT', 'PATCH'],
                'callback' => [$this, 'update_order'],
                'permission_callback' => [$this, 'permission'],
            ],
        ]);

        register_rest_route(self::REST_NS, '/stock/low', [
            'methods' => 'GET',
            'callback' => [$this, 'low_stock'],
            'permission_callback' => [$this, 'permission'],
        ]);

        register_rest_route(self::REST_NS, '/media', [
            'methods' => 'POST',
            'callback' => [$this, 'upload_media'],
            'permission_callback' => [$this, 'permission'],
        ]);
    }

    public function health(): WP_REST_Response {
        return new WP_REST_Response([
            'ok' => true,
            'bridge_version' => self::VERSION,
            'woocommerce' => class_exists('WooCommerce'),
            'site' => home_url('/'),
        ]);
    }

    public function permission(WP_REST_Request $request) {
        $auth = $this->authenticate_request($request);
        return is_wp_error($auth) ? $auth : true;
    }

    private function authenticate_request(WP_REST_Request $request) {
        global $wpdb;

        $header = trim((string) $request->get_header('authorization'));
        $token = '';

        if (preg_match('/^Bearer\s+(.+)$/i', $header, $m)) {
            $token = trim($m[1]);
        }

        if (!$token) {
            $token = trim((string) $request->get_header('x-franluz-token'));
        }

        if (!$token) {
            return new WP_Error('franluz_auth_required', 'Sessão do FranLuz Seller não informada.', ['status' => 401]);
        }

        $hash = hash('sha256', $token);
        $now = gmdate('Y-m-d H:i:s');

        $row = $wpdb->get_row($wpdb->prepare(
            "SELECT * FROM {$this->table} WHERE access_hash = %s AND access_expires_at > %s LIMIT 1",
            $hash,
            $now
        ));

        if (!$row) {
            return new WP_Error('franluz_session_expired', 'Sessão expirada. Entre novamente.', ['status' => 401]);
        }

        $user = get_user_by('id', (int) $row->user_id);
        if (!$user || !$this->allowed_user($user)) {
            return new WP_Error('franluz_forbidden', 'Esta conta não possui acesso ao FranLuz Seller.', ['status' => 403]);
        }

        $this->current_token_id = (int) $row->id;
        wp_set_current_user((int) $user->ID);

        $wpdb->update(
            $this->table,
            ['last_used_at' => $now],
            ['id' => (int) $row->id],
            ['%s'],
            ['%d']
        );

        return $user;
    }

    private function allowed_user(WP_User $user): bool {
        $roles = (array) $user->roles;
        $allowed_roles = ['administrator', 'shop_manager', 'seller', 'vendedor', 'vendor'];

        $allowed = (bool) array_intersect($allowed_roles, $roles)
            || user_can($user, 'manage_woocommerce')
            || user_can($user, 'edit_products');

        return (bool) apply_filters('franluz_seller_allowed_user', $allowed, $user);
    }

    private function login_rate_key(string $username): string {
        $ip = isset($_SERVER['REMOTE_ADDR']) ? sanitize_text_field(wp_unslash($_SERVER['REMOTE_ADDR'])) : 'unknown';
        return 'franluz_login_' . md5(strtolower($username) . '|' . $ip);
    }

    public function login(WP_REST_Request $request) {
        if (!is_ssl()) {
            return new WP_Error('franluz_https_required', 'O FranLuz Seller exige HTTPS.', ['status' => 400]);
        }

        $username = sanitize_text_field((string) $request->get_param('username'));
        $password = (string) $request->get_param('password');
        $device = sanitize_text_field((string) $request->get_param('device_name'));

        if (!$username || !$password) {
            return new WP_Error('franluz_missing_credentials', 'Informe usuário/e-mail e senha.', ['status' => 400]);
        }

        $rate_key = $this->login_rate_key($username);
        $attempts = (int) get_transient($rate_key);

        if ($attempts >= 8) {
            return new WP_Error('franluz_too_many_attempts', 'Muitas tentativas. Aguarde alguns minutos.', ['status' => 429]);
        }

        $user = wp_authenticate($username, $password);

        if (is_wp_error($user)) {
            set_transient($rate_key, $attempts + 1, 15 * MINUTE_IN_SECONDS);
            return new WP_Error('franluz_invalid_login', 'Usuário/e-mail ou senha inválidos.', ['status' => 401]);
        }

        if (!$this->allowed_user($user)) {
            return new WP_Error('franluz_forbidden', 'Esta conta não possui permissão de vendedor.', ['status' => 403]);
        }

        delete_transient($rate_key);
        return rest_ensure_response($this->issue_session($user, $device ?: 'Android'));
    }

    private function random_token(): string {
        return rtrim(strtr(base64_encode(random_bytes(48)), '+/', '-_'), '=');
    }

    private function issue_session(WP_User $user, string $device): array {
        global $wpdb;

        $access = $this->random_token();
        $refresh = $this->random_token();
        $now = time();

        $wpdb->query($wpdb->prepare(
            "DELETE FROM {$this->table} WHERE user_id = %d AND refresh_expires_at <= %s",
            (int) $user->ID,
            gmdate('Y-m-d H:i:s', $now)
        ));

        $wpdb->insert($this->table, [
            'user_id' => (int) $user->ID,
            'access_hash' => hash('sha256', $access),
            'refresh_hash' => hash('sha256', $refresh),
            'device_name' => mb_substr($device, 0, 190),
            'access_expires_at' => gmdate('Y-m-d H:i:s', $now + self::ACCESS_TTL),
            'refresh_expires_at' => gmdate('Y-m-d H:i:s', $now + self::REFRESH_TTL),
            'created_at' => gmdate('Y-m-d H:i:s', $now),
            'last_used_at' => gmdate('Y-m-d H:i:s', $now),
        ], ['%d', '%s', '%s', '%s', '%s', '%s', '%s', '%s']);

        return [
            'access_token' => $access,
            'refresh_token' => $refresh,
            'expires_in' => self::ACCESS_TTL,
            'refresh_expires_in' => self::REFRESH_TTL,
            'user' => $this->user_payload($user),
        ];
    }

    public function refresh(WP_REST_Request $request) {
        global $wpdb;

        $refresh = trim((string) $request->get_param('refresh_token'));
        if (!$refresh) {
            return new WP_Error('franluz_refresh_required', 'Refresh token não informado.', ['status' => 400]);
        }

        $row = $wpdb->get_row($wpdb->prepare(
            "SELECT * FROM {$this->table} WHERE refresh_hash = %s AND refresh_expires_at > %s LIMIT 1",
            hash('sha256', $refresh),
            gmdate('Y-m-d H:i:s')
        ));

        if (!$row) {
            return new WP_Error('franluz_refresh_expired', 'Sessão expirada. Entre novamente.', ['status' => 401]);
        }

        $user = get_user_by('id', (int) $row->user_id);
        if (!$user || !$this->allowed_user($user)) {
            return new WP_Error('franluz_forbidden', 'Acesso de vendedor não autorizado.', ['status' => 403]);
        }

        $device = (string) $row->device_name;
        $wpdb->delete($this->table, ['id' => (int) $row->id], ['%d']);

        return rest_ensure_response($this->issue_session($user, $device));
    }

    public function logout(WP_REST_Request $request) {
        global $wpdb;

        if ($this->current_token_id) {
            $wpdb->delete($this->table, ['id' => $this->current_token_id], ['%d']);
        }

        return rest_ensure_response(['ok' => true]);
    }

    private function user_payload(WP_User $user): array {
        return [
            'id' => (int) $user->ID,
            'name' => $user->display_name,
            'email' => $user->user_email,
            'roles' => array_values((array) $user->roles),
        ];
    }

    public function me(): WP_REST_Response {
        return rest_ensure_response([
            'user' => $this->user_payload(wp_get_current_user()),
            'bridge_version' => self::VERSION,
        ]);
    }

    private function ensure_wc() {
        if (!class_exists('WooCommerce') || !function_exists('wc_get_products') || !function_exists('wc_get_orders')) {
            return new WP_Error('franluz_woocommerce_missing', 'WooCommerce não está disponível.', ['status' => 503]);
        }
        return true;
    }

    private function count_orders(string $status): int {
        $result = wc_get_orders([
            'status' => [$status],
            'limit' => 1,
            'paginate' => true,
            'return' => 'objects',
        ]);

        return is_object($result) && isset($result->total) ? (int) $result->total : 0;
    }

    public function dashboard() {
        $wc = $this->ensure_wc();
        if (is_wp_error($wc)) return $wc;

        $counts = wp_count_posts('product');
        $low = $this->low_stock_products(100);

        return rest_ensure_response([
            'orders' => [
                'pending' => $this->count_orders('pending'),
                'on_hold' => $this->count_orders('on-hold'),
                'processing' => $this->count_orders('processing'),
                'completed' => $this->count_orders('completed'),
            ],
            'products' => [
                'published' => isset($counts->publish) ? (int) $counts->publish : 0,
                'draft' => isset($counts->draft) ? (int) $counts->draft : 0,
                'low_stock' => count($low),
            ],
            'updated_at' => gmdate(DATE_ATOM),
        ]);
    }

    private function product_payload(WC_Product $product): array {
        $images = [];

        foreach (array_values(array_filter(array_merge([$product->get_image_id()], $product->get_gallery_image_ids()))) as $id) {
            $images[] = [
                'id' => (int) $id,
                'src' => (string) wp_get_attachment_image_url($id, 'large'),
                'alt' => (string) get_post_meta($id, '_wp_attachment_image_alt', true),
            ];
        }

        return [
            'id' => $product->get_id(),
            'name' => $product->get_name(),
            'status' => $product->get_status(),
            'type' => $product->get_type(),
            'sku' => $product->get_sku(),
            'price' => $product->get_price(),
            'regular_price' => $product->get_regular_price(),
            'sale_price' => $product->get_sale_price(),
            'stock_status' => $product->get_stock_status(),
            'manage_stock' => $product->get_manage_stock(),
            'stock_quantity' => $product->get_stock_quantity(),
            'weight' => $product->get_weight(),
            'dimensions' => [
                'length' => $product->get_length(),
                'width' => $product->get_width(),
                'height' => $product->get_height(),
            ],
            'images' => $images,
            'categories' => array_map(static function($term) {
                return ['id' => (int) $term->term_id, 'name' => $term->name];
            }, wp_get_post_terms($product->get_id(), 'product_cat')),
            'modified_at' => $product->get_date_modified() ? $product->get_date_modified()->date(DATE_ATOM) : null,
        ];
    }

    public function products(WP_REST_Request $request) {
        $wc = $this->ensure_wc();
        if (is_wp_error($wc)) return $wc;

        $page = max(1, (int) $request->get_param('page'));
        $per_page = min(50, max(1, (int) ($request->get_param('per_page') ?: 20)));
        $search = sanitize_text_field((string) $request->get_param('search'));

        $args = [
            'limit' => $per_page,
            'page' => $page,
            'paginate' => true,
            'status' => ['publish', 'draft', 'pending', 'private'],
            'orderby' => 'date',
            'order' => 'DESC',
            'return' => 'objects',
        ];

        if ($search) $args['s'] = $search;

        $result = wc_get_products($args);
        $items = is_object($result) && isset($result->products) ? $result->products : (array) $result;

        return rest_ensure_response([
            'items' => array_map([$this, 'product_payload'], $items),
            'page' => $page,
            'total' => is_object($result) && isset($result->total) ? (int) $result->total : count($items),
            'total_pages' => is_object($result) && isset($result->max_num_pages) ? (int) $result->max_num_pages : 1,
        ]);
    }

    public function product(WP_REST_Request $request) {
        $wc = $this->ensure_wc();
        if (is_wp_error($wc)) return $wc;

        $product = wc_get_product((int) $request['id']);
        if (!$product) {
            return new WP_Error('franluz_product_not_found', 'Produto não encontrado.', ['status' => 404]);
        }

        return rest_ensure_response($this->product_payload($product));
    }

    private function apply_product_payload(WC_Product $product, array $data): WC_Product {
        if (array_key_exists('name', $data)) $product->set_name(sanitize_text_field((string) $data['name']));
        if (array_key_exists('status', $data)) $product->set_status(sanitize_key((string) $data['status']));
        if (array_key_exists('sku', $data)) $product->set_sku(sanitize_text_field((string) $data['sku']));
        if (array_key_exists('regular_price', $data)) $product->set_regular_price(wc_format_decimal($data['regular_price']));
        if (array_key_exists('sale_price', $data)) $product->set_sale_price(wc_format_decimal($data['sale_price']));
        if (array_key_exists('description', $data)) $product->set_description(wp_kses_post((string) $data['description']));
        if (array_key_exists('short_description', $data)) $product->set_short_description(wp_kses_post((string) $data['short_description']));
        if (array_key_exists('manage_stock', $data)) $product->set_manage_stock((bool) $data['manage_stock']);
        if (array_key_exists('stock_quantity', $data) && $data['stock_quantity'] !== null) $product->set_stock_quantity((int) $data['stock_quantity']);
        if (array_key_exists('stock_status', $data)) $product->set_stock_status(sanitize_key((string) $data['stock_status']));
        if (array_key_exists('weight', $data)) $product->set_weight(wc_format_decimal($data['weight']));

        if (!empty($data['dimensions']) && is_array($data['dimensions'])) {
            $d = $data['dimensions'];
            if (array_key_exists('length', $d)) $product->set_length(wc_format_decimal($d['length']));
            if (array_key_exists('width', $d)) $product->set_width(wc_format_decimal($d['width']));
            if (array_key_exists('height', $d)) $product->set_height(wc_format_decimal($d['height']));
        }

        if (isset($data['category_ids']) && is_array($data['category_ids'])) {
            $product->set_category_ids(array_values(array_filter(array_map('absint', $data['category_ids']))));
        }

        if (isset($data['image_ids']) && is_array($data['image_ids'])) {
            $ids = array_values(array_filter(array_map('absint', $data['image_ids'])));
            $product->set_image_id($ids[0] ?? 0);
            $product->set_gallery_image_ids(array_slice($ids, 1));
        }

        return $product;
    }

    public function create_product(WP_REST_Request $request) {
        $wc = $this->ensure_wc();
        if (is_wp_error($wc)) return $wc;

        try {
            $product = new WC_Product_Simple();
            $this->apply_product_payload($product, (array) $request->get_json_params());

            if (!$product->get_name()) {
                return new WP_Error('franluz_product_name_required', 'Informe o nome do produto.', ['status' => 400]);
            }

            $product->save();
            return new WP_REST_Response($this->product_payload($product), 201);
        } catch (Throwable $e) {
            return new WP_Error('franluz_product_create_failed', $e->getMessage(), ['status' => 400]);
        }
    }

    public function update_product(WP_REST_Request $request) {
        $wc = $this->ensure_wc();
        if (is_wp_error($wc)) return $wc;

        $product = wc_get_product((int) $request['id']);
        if (!$product) {
            return new WP_Error('franluz_product_not_found', 'Produto não encontrado.', ['status' => 404]);
        }

        try {
            $this->apply_product_payload($product, (array) $request->get_json_params());
            $product->save();
            return rest_ensure_response($this->product_payload($product));
        } catch (Throwable $e) {
            return new WP_Error('franluz_product_update_failed', $e->getMessage(), ['status' => 400]);
        }
    }

    private function order_payload(WC_Order $order): array {
        $items = [];

        foreach ($order->get_items() as $item) {
            $items[] = [
                'name' => $item->get_name(),
                'quantity' => $item->get_quantity(),
                'total' => $item->get_total(),
                'product_id' => $item->get_product_id(),
                'variation_id' => $item->get_variation_id(),
            ];
        }

        return [
            'id' => $order->get_id(),
            'number' => $order->get_order_number(),
            'status' => $order->get_status(),
            'status_name' => wc_get_order_status_name($order->get_status()),
            'date_created' => $order->get_date_created() ? $order->get_date_created()->date(DATE_ATOM) : null,
            'total' => $order->get_total(),
            'currency' => $order->get_currency(),
            'payment_method' => $order->get_payment_method_title(),
            'shipping_total' => $order->get_shipping_total(),
            'customer' => [
                'name' => trim($order->get_formatted_billing_full_name()),
                'email' => $order->get_billing_email(),
                'phone' => $order->get_billing_phone(),
            ],
            'items' => $items,
            'item_count' => $order->get_item_count(),
        ];
    }

    public function orders(WP_REST_Request $request) {
        $wc = $this->ensure_wc();
        if (is_wp_error($wc)) return $wc;

        $page = max(1, (int) $request->get_param('page'));
        $per_page = min(50, max(1, (int) ($request->get_param('per_page') ?: 20)));
        $status = sanitize_key((string) $request->get_param('status'));

        $args = [
            'limit' => $per_page,
            'page' => $page,
            'paginate' => true,
            'orderby' => 'date',
            'order' => 'DESC',
            'return' => 'objects',
        ];
        if ($status) $args['status'] = [$status];

        $result = wc_get_orders($args);
        $items = is_object($result) && isset($result->orders) ? $result->orders : (array) $result;

        return rest_ensure_response([
            'items' => array_map([$this, 'order_payload'], $items),
            'page' => $page,
            'total' => is_object($result) && isset($result->total) ? (int) $result->total : count($items),
            'total_pages' => is_object($result) && isset($result->max_num_pages) ? (int) $result->max_num_pages : 1,
        ]);
    }

    public function order(WP_REST_Request $request) {
        $wc = $this->ensure_wc();
        if (is_wp_error($wc)) return $wc;

        $order = wc_get_order((int) $request['id']);
        if (!$order) {
            return new WP_Error('franluz_order_not_found', 'Pedido não encontrado.', ['status' => 404]);
        }

        return rest_ensure_response($this->order_payload($order));
    }

    public function update_order(WP_REST_Request $request) {
        $wc = $this->ensure_wc();
        if (is_wp_error($wc)) return $wc;

        $order = wc_get_order((int) $request['id']);
        if (!$order) {
            return new WP_Error('franluz_order_not_found', 'Pedido não encontrado.', ['status' => 404]);
        }

        $data = (array) $request->get_json_params();
        $status = isset($data['status']) ? sanitize_key((string) $data['status']) : '';

        if ($status) {
            $valid = array_map(static fn($key) => str_replace('wc-', '', $key), array_keys(wc_get_order_statuses()));
            if (!in_array($status, $valid, true)) {
                return new WP_Error('franluz_invalid_order_status', 'Status de pedido inválido.', ['status' => 400]);
            }
            $order->update_status($status, isset($data['note']) ? sanitize_textarea_field((string) $data['note']) : '', true);
        } elseif (!empty($data['note'])) {
            $order->add_order_note(sanitize_textarea_field((string) $data['note']), false, true);
        }

        $order->save();
        return rest_ensure_response($this->order_payload($order));
    }

    private function low_stock_products(int $limit): array {
        $products = wc_get_products([
            'limit' => max(1, $limit),
            'status' => ['publish', 'draft', 'private'],
            'manage_stock' => true,
            'orderby' => 'date',
            'order' => 'DESC',
            'return' => 'objects',
        ]);

        $low = [];

        foreach ($products as $product) {
            $qty = $product->get_stock_quantity();
            if ($qty === null) continue;

            $threshold = function_exists('wc_get_low_stock_amount')
                ? wc_get_low_stock_amount($product)
                : (int) get_option('woocommerce_notify_low_stock_amount', 2);

            if ($qty <= $threshold) {
                $low[] = $this->product_payload($product);
            }
        }

        return $low;
    }

    public function low_stock() {
        $wc = $this->ensure_wc();
        if (is_wp_error($wc)) return $wc;

        return rest_ensure_response([
            'items' => $this->low_stock_products(100),
        ]);
    }

    public function upload_media(WP_REST_Request $request) {
        if (empty($_FILES['file']) || !is_uploaded_file($_FILES['file']['tmp_name'])) {
            return new WP_Error('franluz_media_required', 'Envie um arquivo no campo file.', ['status' => 400]);
        }

        require_once ABSPATH . 'wp-admin/includes/file.php';
        require_once ABSPATH . 'wp-admin/includes/media.php';
        require_once ABSPATH . 'wp-admin/includes/image.php';

        $attachment_id = media_handle_upload('file', 0);

        if (is_wp_error($attachment_id)) {
            return $attachment_id;
        }

        return new WP_REST_Response([
            'id' => (int) $attachment_id,
            'url' => wp_get_attachment_url($attachment_id),
            'mime_type' => get_post_mime_type($attachment_id),
        ], 201);
    }

    public function register_admin_page(): void {
        add_management_page(
            'FranLuz Seller Bridge',
            'FranLuz Seller Bridge',
            'manage_options',
            'franluz-seller-bridge',
            [$this, 'admin_page']
        );
    }

    public function admin_page(): void {
        if (!current_user_can('manage_options')) return;

        echo '<div class="wrap"><h1>FranLuz Seller Bridge</h1>';
        echo '<p><strong>Versão:</strong> ' . esc_html(self::VERSION) . '</p>';
        echo '<p><strong>API:</strong> <code>' . esc_html(rest_url(self::REST_NS . '/health')) . '</code></p>';
        echo '<p><strong>WooCommerce:</strong> ' . (class_exists('WooCommerce') ? 'Ativo' : 'Não detectado') . '</p>';
        echo '<p>O aplicativo Android usa esta API. Nenhuma Consumer Key/Secret do WooCommerce é armazenada no APK.</p>';
        echo '</div>';
    }
}

register_activation_hook(__FILE__, ['FranLuz_Seller_Bridge', 'activate']);
new FranLuz_Seller_Bridge();
