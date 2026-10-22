package io.everytrade.server.plugin.impl.everytrade;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.everytrade.server.plugin.impl.everytrade.helius.HeliusClient;
import si.mazi.rescu.RestProxyFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A local stand-in for both Helius endpoints the SOL connector reads:
 * <ul>
 *   <li>the Wallet API {@code GET /v1/wallet/{address}/history} (successful transactions only, newest first, paged by
 *   {@code limit} and the {@code before} signature cursor), and</li>
 *   <li>the JSON-RPC endpoint ({@code POST /?api-key=...}) answering {@code getSignaturesForAddress} (every signature
 *   of the address, failed ones included, newest first, {@code before}/{@code until} exclusive, at most
 *   {@code limit}) and {@code getTransaction} (fee payer in {@code accountKeys[0]}, fee in {@code meta.fee}).</li>
 * </ul>
 * Events are added in chronological order (oldest first); each gets the next slot and a block time derived from it.
 * <p>
 * The real {@link HeliusClient} is used: every interface-typed field of it (the rescu proxies) is re-pointed at this
 * server, so the tests cover request parameters and JSON-to-DTO mapping too, and they compile against a client that
 * does not yet have an RPC proxy - a missing RPC stream shows up as missing requests, not as a compile error.
 */
final class FakeHelius implements AutoCloseable {

    static final String WALLET_HISTORY = "walletHistory";
    static final String GET_SIGNATURES = "getSignaturesForAddress";
    static final String GET_TRANSACTION = "getTransaction";

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final long FIRST_SLOT = 300_000_000L;

    private final HttpServer server;
    private final String address;
    /** Chronological: oldest first. */
    private final List<Event> events = new ArrayList<>();
    private final List<Request> requests = Collections.synchronizedList(new ArrayList<>());
    private volatile Map<String, Object> rpcError;
    private final java.util.Set<String> withoutBalances = java.util.concurrent.ConcurrentHashMap.newKeySet();
    /** Canned failures served (and consumed) before any normal answer, one per request, in order. */
    private final java.util.Deque<Failure> failures = new java.util.concurrent.ConcurrentLinkedDeque<>();
    /** Like {@link #failures}, but consumed by JSON-RPC requests only. */
    private final java.util.Deque<Failure> rpcFailures = new java.util.concurrent.ConcurrentLinkedDeque<>();

    FakeHelius(String address) throws IOException {
        this.address = address;
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();
    }

    /** A client whose proxies talk to this server instead of api.helius.xyz / mainnet.helius-rpc.com. */
    HeliusClient client() {
        final HeliusClient client = new HeliusClient();
        final String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/";
        try {
            for (Field field : HeliusClient.class.getDeclaredFields()) {
                if (field.getType().isInterface() && !Modifier.isStatic(field.getModifiers())) {
                    field.setAccessible(true);
                    field.set(client, RestProxyFactory.createProxy(field.getType(), url));
                }
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        return client;
    }

    /**
     * A successful transaction: listed by the Wallet API with the given row and by getSignaturesForAddress with
     * {@code err: null}.
     */
    FakeHelius success(String signature, long feeLamports, String feePayer, List<Map<String, Object>> balanceChanges) {
        events.add(new Event(signature, nextSlot(), null, feeLamports, feePayer, balanceChanges, true, null, false));
        return this;
    }

    /**
     * A successful transaction whose on-chain lamport change of the wallet ({@code postBalance - preBalance} of the
     * wallet's account in {@code getTransaction}, fee INCLUDED when the wallet paid it) is {@code walletLamportDelta},
     * whatever the Wallet API's {@code balanceChanges} say - the shape of a Helius row that omits a rent refund.
     */
    FakeHelius successOnChain(String signature, long feeLamports, String feePayer,
                              List<Map<String, Object>> balanceChanges, long walletLamportDelta) {
        events.add(new Event(signature, nextSlot(), null, feeLamports, feePayer, balanceChanges, true,
            walletLamportDelta, false));
        return this;
    }

    /**
     * Like {@link #successOnChain}, but a version-0 transaction that reaches the wallet through an address lookup
     * table: the wallet is not in {@code message.accountKeys} but in {@code meta.loadedAddresses.writable}, and its
     * balances follow the static keys in {@code pre/postBalances}.
     */
    FakeHelius successViaLookupTable(String signature, long feeLamports, String feePayer,
                                     List<Map<String, Object>> balanceChanges, long walletLamportDelta) {
        events.add(new Event(signature, nextSlot(), null, feeLamports, feePayer, balanceChanges, true,
            walletLamportDelta, true));
        return this;
    }

    /** The next getTransaction answers of these signatures carry no pre/postBalances (an incomplete node answer). */
    FakeHelius withoutBalances(String signature) {
        withoutBalances.add(signature);
        return this;
    }

    /** A failed transaction: absent from the Wallet API, listed by getSignaturesForAddress with a non-null err. */
    FakeHelius failed(String signature, long feeLamports, String feePayer) {
        events.add(new Event(signature, nextSlot(), Map.of("InstructionError", List.of(0, "Custom")), feeLamports,
            feePayer, List.of(), false, null, false));
        return this;
    }

    /**
     * A failed transaction that the Wallet API DOES list, with its {@code error} set (the live API has been seen to
     * omit them; this covers the day it does not).
     */
    FakeHelius walletFailed(String signature, long feeLamports, String feePayer) {
        events.add(new Event(signature, nextSlot(), Map.of("InstructionError", List.of(0, "Custom")), feeLamports,
            feePayer, List.of(), true, null, false));
        return this;
    }

    /**
     * A successful signature the Wallet API does not list as a row of its own (only getSignaturesForAddress sees
     * it), used to fill RPC pages cheaply.
     */
    FakeHelius rpcOnlySuccess(String signature) {
        events.add(new Event(signature, nextSlot(), null, 5000L, "someone-else", List.of(), false, null, false));
        return this;
    }

    /** Every RPC call from now on answers with this JSON-RPC error object (HTTP 200, as Helius does). */
    FakeHelius rpcError(int code, String message) {
        this.rpcError = Map.of("code", code, "message", message);
        return this;
    }

    /**
     * The next {@code times} requests (either endpoint) answer HTTP {@code status} with {@code body} instead of data.
     * Status 200 with a JSON-RPC {@code error} body is how Helius reports some RPC failures.
     */
    FakeHelius failNext(int times, int status, String body) {
        for (int i = 0; i < times; i++) {
            failures.add(new Failure(status, body));
        }
        return this;
    }

    /** Like {@link #failNext}, but only JSON-RPC requests consume these (the Wallet API keeps answering normally). */
    FakeHelius failNextRpc(int times, int status, String body) {
        for (int i = 0; i < times; i++) {
            rpcFailures.add(new Failure(status, body));
        }
        return this;
    }

    /**
     * The next {@code times} JSON-RPC requests answer HTTP 200 with an EMPTY body (no JSON-RPC envelope at all), which
     * rescu turns into a {@code null} response object. Recorded as {@code "empty-200"}.
     */
    FakeHelius emptyRpcBodyNext(int times) {
        for (int i = 0; i < times; i++) {
            rpcFailures.add(new Failure(200, ""));
        }
        return this;
    }

    static Map<String, Object> nativeChange(String mint, String amount) {
        final Map<String, Object> change = new LinkedHashMap<>();
        change.put("mint", mint);
        change.put("amount", new BigDecimal(amount));
        change.put("decimals", 9);
        return change;
    }

    static Map<String, Object> tokenChange(String mint, String amount, int decimals) {
        final Map<String, Object> change = new LinkedHashMap<>();
        change.put("mint", mint);
        change.put("amount", new BigDecimal(amount));
        change.put("decimals", decimals);
        return change;
    }

    /** Synthetic block clock: 1.7e9 s + 10 s per event slot, strictly increasing like the real one. */
    static long blockTimeOf(long slot) {
        return 1_700_000_000L + (slot - FIRST_SLOT) * 10L;
    }

    long slotOf(String signature) {
        return events.stream().filter(e -> e.signature.equals(signature)).findFirst().orElseThrow().slot;
    }

    List<Request> requests() {
        synchronized (requests) {
            return new ArrayList<>(requests);
        }
    }

    List<Request> requests(String method) {
        return requests().stream().filter(r -> method.equals(r.method)).collect(Collectors.toList());
    }

    void clearRequests() {
        requests.clear();
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private long nextSlot() {
        return FIRST_SLOT + events.size();
    }

    // --- server -----------------------------------------------------------------------------------------------------

    private void handle(HttpExchange exchange) throws IOException {
        final Map<String, String> q = query(exchange.getRequestURI().getRawQuery());
        final String path = exchange.getRequestURI().getPath();
        Failure failure = failures.poll();
        if (failure == null && "POST".equals(exchange.getRequestMethod())) {
            failure = rpcFailures.poll();
        }
        if (failure != null) {
            final byte[] bytes = failure.body.getBytes(StandardCharsets.UTF_8);
            requests.add(new Request((bytes.length == 0 ? "empty-" : "failure-") + failure.status, q.get("api-key"),
                Map.of()));
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            // -1 = Content-Length: 0; a length of 0 would switch the JDK server to a chunked body
            exchange.sendResponseHeaders(failure.status, bytes.length == 0 ? -1 : bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
            return;
        }
        final Object body;
        if ("GET".equals(exchange.getRequestMethod()) && path.startsWith("/v1/wallet/")) {
            final String walletAddress = path.substring("/v1/wallet/".length(), path.lastIndexOf("/history"));
            requests.add(new Request(WALLET_HISTORY, q.get("api-key"), Map.of("address", walletAddress,
                "before", String.valueOf(q.get("before")), "limit", String.valueOf(q.get("limit")))));
            body = walletHistory(Integer.parseInt(q.getOrDefault("limit", "100")), q.get("before"));
        } else if ("POST".equals(exchange.getRequestMethod())) {
            final Map<?, ?> rpc;
            try (InputStream in = exchange.getRequestBody()) {
                rpc = JSON.readValue(in, Map.class);
            }
            final String method = (String) rpc.get("method");
            final List<?> params = (List<?>) rpc.get("params");
            final Map<String, Object> recorded = new HashMap<>();
            recorded.put("params", params);
            recorded.put("jsonrpc", rpc.get("jsonrpc"));
            requests.add(new Request(method, q.get("api-key"), recorded));
            body = rpc(method, params, rpc.get("id"));
        } else {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
            return;
        }
        final byte[] bytes = JSON.writeValueAsBytes(body);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private Map<String, Object> walletHistory(int limit, String before) {
        final List<Event> newestFirst = new ArrayList<>(events);
        Collections.reverse(newestFirst);
        final List<Event> listed = newestFirst.stream().filter(e -> e.inWalletApi).collect(Collectors.toList());
        int from = 0;
        if (before != null) {
            from = listed.size();
            for (int i = 0; i < listed.size(); i++) {
                if (listed.get(i).signature.equals(before)) {
                    from = i + 1;
                    break;
                }
            }
        }
        final int to = Math.min(listed.size(), from + limit);
        final List<Map<String, Object>> data = new ArrayList<>();
        for (Event e : listed.subList(from, to)) {
            final Map<String, Object> row = new LinkedHashMap<>();
            row.put("signature", e.signature);
            row.put("timestamp", blockTimeOf(e.slot));
            row.put("slot", e.slot);
            row.put("fee", e.feeLamports);
            row.put("feePayer", e.feePayer);
            row.put("error", e.err);
            row.put("balanceChanges", e.balanceChanges);
            data.add(row);
        }
        final Map<String, Object> pagination = new LinkedHashMap<>();
        pagination.put("hasMore", to < listed.size());
        pagination.put("nextCursor", data.isEmpty() ? null : data.get(data.size() - 1).get("signature"));
        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("data", data);
        body.put("pagination", pagination);
        return body;
    }

    private Map<String, Object> rpc(String method, List<?> params, Object id) {
        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("jsonrpc", "2.0");
        body.put("id", id);
        if (rpcError != null) {
            body.put("error", rpcError);
            return body;
        }
        if (GET_SIGNATURES.equals(method)) {
            body.put("result", signatures((String) params.get(0), (Map<?, ?>) params.get(1)));
        } else if (GET_TRANSACTION.equals(method)) {
            body.put("result", transaction((String) params.get(0)));
        } else {
            body.put("error", Map.of("code", -32601, "message", "Method not found"));
        }
        return body;
    }

    private List<Map<String, Object>> signatures(String forAddress, Map<?, ?> options) {
        final List<Map<String, Object>> result = new ArrayList<>();
        if (!address.equals(forAddress)) {
            return result;
        }
        final String before = options == null ? null : (String) options.get("before");
        final String until = options == null ? null : (String) options.get("until");
        final int limit = options == null || options.get("limit") == null ? 1000 : ((Number) options.get("limit")).intValue();
        final List<Event> newestFirst = new ArrayList<>(events);
        Collections.reverse(newestFirst);
        boolean started = before == null;
        for (Event e : newestFirst) {
            if (!started) {
                started = e.signature.equals(before);
                continue;
            }
            if (e.signature.equals(until) || result.size() >= limit) {
                break;
            }
            final Map<String, Object> row = new LinkedHashMap<>();
            row.put("signature", e.signature);
            row.put("slot", e.slot);
            row.put("err", e.err);
            row.put("memo", null);
            row.put("blockTime", blockTimeOf(e.slot));
            row.put("confirmationStatus", "finalized");
            result.add(row);
        }
        return result;
    }

    /**
     * {@code getTransaction} (encoding json). The account list is {@code [feePayer, other]} where {@code other} is the
     * wallet when someone else paid; with {@link Event#viaLookupTable} the wallet is moved to
     * {@code meta.loadedAddresses.writable}. The fee payer's balance drops by the fee; the wallet's balance changes by
     * {@link Event#walletLamportDelta} - by default what the Wallet API row implies (native sum, minus the fee when the
     * wallet paid), so a test that does not care about the chain sees the Helius numbers.
     */
    private Map<String, Object> transaction(String signature) {
        final Event e = events.stream().filter(it -> it.signature.equals(signature)).findFirst().orElse(null);
        if (e == null) {
            return null;
        }
        final String payer = e.feePayer == null ? "UnknownPayer1111111111111111111111111111111" : e.feePayer;
        final boolean walletPays = address.equals(payer);
        final long walletDelta = e.walletLamportDelta != null ? e.walletLamportDelta : impliedWalletDelta(e, walletPays);
        final List<String> staticKeys = new ArrayList<>();
        final List<Long> pre = new ArrayList<>();
        final List<Long> post = new ArrayList<>();
        final long walletPre = 50_000_000_000L;
        final long otherPre = 7_000_000_000L;
        staticKeys.add(payer);
        if (walletPays) {
            pre.add(walletPre);
            post.add(walletPre + walletDelta);
        } else {
            pre.add(otherPre);
            post.add(otherPre - e.feeLamports);
        }
        final List<String> writable = new ArrayList<>();
        if (!walletPays) {
            if (e.viaLookupTable) {
                writable.add(address);
            } else {
                staticKeys.add(address);
            }
        } else {
            staticKeys.add("11111111111111111111111111111111");
        }
        // static keys first, then loaded writable, as the node orders pre/postBalances
        if (walletPays) {
            pre.add(1L);
            post.add(1L);
        } else if (!e.viaLookupTable) {
            pre.add(walletPre);
            post.add(walletPre + walletDelta);
        }
        staticKeys.add("ComputeBudget111111111111111111111111111111");
        pre.add(1L);
        post.add(1L);
        if (!walletPays && e.viaLookupTable) {
            pre.add(walletPre);
            post.add(walletPre + walletDelta);
        }
        final Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("err", e.err);
        meta.put("fee", e.feeLamports);
        if (!withoutBalances.contains(signature)) {
            meta.put("preBalances", pre);
            meta.put("postBalances", post);
        }
        final Map<String, Object> loaded = new LinkedHashMap<>();
        loaded.put("writable", writable);
        loaded.put("readonly", List.of());
        meta.put("loadedAddresses", loaded);
        final Map<String, Object> message = new LinkedHashMap<>();
        message.put("accountKeys", staticKeys);
        message.put("recentBlockhash", "EkSnNWid2cvwEVnVx9aBqawnmiCNiDgp3gUdkDPTKN1N");
        message.put("instructions", List.of());
        final Map<String, Object> tx = new LinkedHashMap<>();
        tx.put("signatures", List.of(e.signature));
        tx.put("message", message);
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("slot", e.slot);
        result.put("blockTime", blockTimeOf(e.slot));
        result.put("meta", meta);
        result.put("transaction", tx);
        result.put("version", 0);
        return result;
    }

    private static long impliedWalletDelta(Event e, boolean walletPays) {
        if (e.err != null) {
            return walletPays ? -e.feeLamports : 0L;
        }
        final BigDecimal nativeSum = e.balanceChanges == null ? BigDecimal.ZERO : e.balanceChanges.stream()
            .filter(c -> "So11111111111111111111111111111111111111111".equals(c.get("mint")))
            .map(c -> (BigDecimal) c.get("amount"))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        return nativeSum.movePointRight(9).longValueExact() - (walletPays ? e.feeLamports : 0L);
    }

    private static Map<String, String> query(String raw) {
        final Map<String, String> q = new HashMap<>();
        if (raw == null) {
            return q;
        }
        for (String pair : raw.split("&")) {
            final int eq = pair.indexOf('=');
            if (eq > 0) {
                q.put(URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8),
                    URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8));
            }
        }
        return q;
    }

    private static final class Failure {
        final int status;
        final String body;

        Failure(int status, String body) {
            this.status = status;
            this.body = body;
        }
    }

    private static final class Event {
        final String signature;
        final long slot;
        final Object err;
        final long feeLamports;
        final String feePayer;
        final List<Map<String, Object>> balanceChanges;
        final boolean inWalletApi;
        /** The wallet's on-chain lamport change, fee included; {@code null} = implied by the Wallet API row. */
        final Long walletLamportDelta;
        final boolean viaLookupTable;

        Event(String signature, long slot, Object err, long feeLamports, String feePayer,
              List<Map<String, Object>> balanceChanges, boolean inWalletApi, Long walletLamportDelta,
              boolean viaLookupTable) {
            this.signature = signature;
            this.slot = slot;
            this.err = err;
            this.feeLamports = feeLamports;
            this.feePayer = feePayer;
            this.balanceChanges = balanceChanges;
            this.inWalletApi = inWalletApi;
            this.walletLamportDelta = walletLamportDelta;
            this.viaLookupTable = viaLookupTable;
        }
    }

    static final class Request {
        final String method;
        final String apiKey;
        final Map<String, Object> details;
        /** {@link System#nanoTime()} when the request reached this server; the pacing tests measure gaps with it. */
        final long receivedNanos = System.nanoTime();

        Request(String method, String apiKey, Map<String, ?> details) {
            this.method = method;
            this.apiKey = apiKey;
            this.details = new HashMap<>(details);
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> options() {
            final List<Object> params = (List<Object>) details.get("params");
            return params != null && params.size() > 1 ? (Map<String, Object>) params.get(1) : Map.of();
        }

        String firstParam() {
            final List<?> params = (List<?>) details.get("params");
            return params == null || params.isEmpty() ? null : (String) params.get(0);
        }

        @Override
        public String toString() {
            return method + details;
        }
    }
}
