package io.everytrade.server.plugin.impl.everytrade;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.everytrade.server.plugin.impl.everytrade.etherscan.EtherScanClient;
import io.everytrade.server.plugin.impl.everytrade.etherscan.EtherScanV1API;
import si.mazi.rescu.RestProxyFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A local stand-in for the Etherscan v2 {@code /api} endpoint, serving rows shaped exactly like the real JSON payloads
 * (every value a string, as Etherscan sends it) for {@code account/txlist}, {@code account/tokentx},
 * {@code account/txlistinternal} and {@code block/getblocknobytime}.
 * <p>
 * The real {@link EtherScanClient} is used and only its rescu proxy is re-pointed at this server, so the tests cover the
 * request parameters and the JSON-to-DTO mapping too, and they compile and run against a connector that does not yet
 * know a stream: a missing {@code txlistinternal} call simply shows up as a missing request, not as a compile error.
 * <p>
 * Every request is recorded with its arrival time, so a test can assert the window it asked for and the pacing.
 * Windows and pages behave like Etherscan's: rows filtered to {@code startblock <= blockNumber <= endblock}, sorted
 * ascending, then cut into pages of {@code offset}; an empty result is {@code status 0 / "No transactions found"}.
 */
final class FakeEtherscan implements AutoCloseable {

    static final String TXLIST = "txlist";
    static final String TOKENTX = "tokentx";
    static final String INTERNAL = "txlistinternal";
    static final String BLOCK = "getblocknobytime";

    private static final ObjectMapper JSON = new ObjectMapper();

    private final HttpServer server;
    private final Map<String, List<Map<String, String>>> rows = new HashMap<>();
    private final List<Request> requests = Collections.synchronizedList(new ArrayList<>());
    private volatile long headBlock = 20_000_000L;
    /** Canned answers served (and consumed) before any normal answer, one per request, in order. */
    private final java.util.Deque<Failure> failures = new java.util.concurrent.ConcurrentLinkedDeque<>();

    FakeEtherscan() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api", this::handle);
        server.start();
        rows.put(TXLIST, new ArrayList<>());
        rows.put(TOKENTX, new ArrayList<>());
        rows.put(INTERNAL, new ArrayList<>());
    }

    /** A client whose proxy talks to this server instead of api.etherscan.io. */
    EtherScanClient client() {
        final EtherScanClient client = new EtherScanClient();
        try {
            final Field api = EtherScanClient.class.getDeclaredField("api");
            api.setAccessible(true);
            api.set(client, RestProxyFactory.createProxy(
                EtherScanV1API.class, "http://127.0.0.1:" + server.getAddress().getPort() + "/"));
            // Keep rate-limit retries fast; tolerant of a client that has no such field.
            for (Field f : EtherScanClient.class.getDeclaredFields()) {
                if (f.getName().equals("rateLimitBackoffMs")) {
                    f.setAccessible(true);
                    f.setLong(client, 10L);
                }
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        return client;
    }

    /**
     * The next {@code times} requests answer HTTP 200 with Etherscan's error envelope
     * {@code {"status":"0","message":<message>,"result":<result>}} - the way Etherscan reports a bad key, a spent quota
     * or a per-second rate limit.
     */
    FakeEtherscan rejectNext(int times, String message, String result) {
        return rejectNextOn(null, times, message, result);
    }

    /** Like {@link #rejectNext}, but only requests of {@code action} ({@code null} = any) consume these answers. */
    FakeEtherscan rejectNextOn(String action, int times, String message, String result) {
        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "0");
        body.put("message", message);
        body.put("result", result);
        try {
            final String json = JSON.writeValueAsString(body);
            for (int i = 0; i < times; i++) {
                failures.add(new Failure(action, 200, json));
            }
            return this;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** The next {@code times} requests answer HTTP {@code status} with {@code body}. */
    FakeEtherscan failNext(int times, int status, String body) {
        for (int i = 0; i < times; i++) {
            failures.add(new Failure(null, status, body));
        }
        return this;
    }

    FakeEtherscan head(long block) {
        this.headBlock = block;
        return this;
    }

    FakeEtherscan add(String action, Map<String, String> row) {
        rows.get(action).add(row);
        return this;
    }

    List<Request> requests() {
        synchronized (requests) {
            return new ArrayList<>(requests);
        }
    }

    List<Request> requests(String action) {
        return requests().stream().filter(r -> action.equals(r.action)).collect(Collectors.toList());
    }

    void clearRequests() {
        requests.clear();
    }

    @Override
    public void close() {
        server.stop(0);
    }

    // --- rows shaped like the real payloads -------------------------------------------------------------------------

    /** One {@code account/txlist} row. {@code to} empty means contract creation, as on Etherscan. */
    static Map<String, String> normal(String hash, long block, String from, String to, String valueWei, long gasUsed,
                                      long gasPrice, int isError) {
        final Map<String, String> r = new LinkedHashMap<>();
        r.put("blockNumber", String.valueOf(block));
        r.put("timeStamp", String.valueOf(timeOf(block)));
        r.put("hash", hash);
        r.put("nonce", "1");
        r.put("blockHash", "0xb" + block);
        r.put("transactionIndex", "3");
        r.put("from", from);
        r.put("to", to);
        r.put("value", valueWei);
        r.put("gas", String.valueOf(gasUsed));
        r.put("gasPrice", String.valueOf(gasPrice));
        r.put("isError", String.valueOf(isError));
        r.put("txreceipt_status", isError == 0 ? "1" : "0");
        r.put("input", "0x");
        r.put("contractAddress", "");
        r.put("cumulativeGasUsed", "11492524");
        r.put("gasUsed", String.valueOf(gasUsed));
        r.put("confirmations", "14724155");
        r.put("methodId", "0x");
        r.put("functionName", "");
        return r;
    }

    /** One {@code account/tokentx} row; the gas fields are those of the top-level transaction, as on Etherscan. */
    static Map<String, String> token(String hash, long block, String from, String to, String value, String contract,
                                     String symbol, int decimals, long gasUsed, long gasPrice) {
        final Map<String, String> r = new LinkedHashMap<>();
        r.put("blockNumber", String.valueOf(block));
        r.put("timeStamp", String.valueOf(timeOf(block)));
        r.put("hash", hash);
        r.put("nonce", "7");
        r.put("blockHash", "0xb" + block);
        r.put("from", from);
        r.put("contractAddress", contract);
        r.put("to", to);
        r.put("value", value);
        r.put("tokenName", symbol + " token");
        r.put("tokenSymbol", symbol);
        r.put("tokenDecimal", String.valueOf(decimals));
        r.put("transactionIndex", "18");
        r.put("gas", String.valueOf(gasUsed + 1000));
        r.put("gasPrice", String.valueOf(gasPrice));
        r.put("gasUsed", String.valueOf(gasUsed));
        r.put("cumulativeGasUsed", "1000000");
        r.put("input", "deprecated");
        r.put("methodId", "0xa9059cbb");
        r.put("functionName", "transfer(address _to, uint256 _value)");
        r.put("confirmations", "13000000");
        return r;
    }

    /**
     * One {@code account/txlistinternal} row. Shape copied from the live answer of 2026-10-20: no gasPrice, no nonce,
     * a {@code traceId} that tells apart several internal calls of one transaction, {@code isError} and {@code errCode}.
     */
    static Map<String, String> internal(String hash, long block, String from, String to, String valueWei, String traceId,
                                        int isError) {
        final Map<String, String> r = new LinkedHashMap<>();
        r.put("blockNumber", String.valueOf(block));
        r.put("transactionIndex", "18");
        r.put("timeStamp", String.valueOf(timeOf(block)));
        r.put("hash", hash);
        r.put("from", from);
        r.put("to", to);
        r.put("value", valueWei);
        r.put("contractAddress", "");
        r.put("input", "");
        r.put("type", "call");
        r.put("gas", "2300");
        r.put("gasUsed", "0");
        r.put("traceId", traceId);
        r.put("isError", String.valueOf(isError));
        r.put("errCode", isError == 0 ? "" : "Reverted");
        return r;
    }

    /** Synthetic block clock: 1.5e9 s + 13 s per block, strictly increasing like the real one. */
    static long timeOf(long block) {
        return 1_500_000_000L + block * 13L;
    }

    // --- server -----------------------------------------------------------------------------------------------------

    private void handle(HttpExchange exchange) throws IOException {
        final Map<String, String> q = query(exchange.getRequestURI().getRawQuery());
        final Request request = new Request(System.nanoTime(), q);
        requests.add(request);
        Failure failure = null;
        synchronized (failures) {
            for (Failure f : failures) {
                if (f.action == null || f.action.equals(request.action)) {
                    failure = f;
                    break;
                }
            }
            if (failure != null) {
                failures.remove(failure);
            }
        }
        if (failure != null) {
            final byte[] bytes = failure.body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(failure.status, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
            return;
        }

        final Map<String, Object> body = new LinkedHashMap<>();
        if (BLOCK.equals(request.action)) {
            body.put("status", "1");
            body.put("message", "OK");
            body.put("result", String.valueOf(headBlock));
        } else {
            final List<Map<String, String>> source = rows.getOrDefault(request.action, List.of());
            final List<Map<String, String>> window = source.stream()
                .filter(r -> addressMatches(r, request.address))
                .filter(r -> block(r) >= request.startBlock && block(r) <= request.endBlock)
                .sorted(Comparator.comparingLong(FakeEtherscan::block))
                .collect(Collectors.toList());
            final int from = Math.min(window.size(), (request.page - 1) * request.offset);
            final int to = Math.min(window.size(), from + request.offset);
            final List<Map<String, String>> page = window.subList(from, to);
            body.put("status", page.isEmpty() ? "0" : "1");
            body.put("message", page.isEmpty() ? "No transactions found" : "OK");
            body.put("result", page);
        }
        final byte[] bytes = JSON.writeValueAsBytes(body);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static boolean addressMatches(Map<String, String> row, String address) {
        return address == null || address.equals(row.get("from")) || address.equals(row.get("to"));
    }

    private static long block(Map<String, String> row) {
        return Long.parseLong(row.get("blockNumber"));
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
        final String action;
        final int status;
        final String body;

        Failure(String action, int status, String body) {
            this.action = action;
            this.status = status;
            this.body = body;
        }
    }

    static final class Request {
        final long nanoTime;
        final String action;
        final String address;
        final long startBlock;
        final long endBlock;
        final int page;
        final int offset;
        final String chainId;

        Request(long nanoTime, Map<String, String> q) {
            this.nanoTime = nanoTime;
            this.action = q.get("action");
            this.address = q.get("address");
            this.startBlock = q.containsKey("startblock") ? Long.parseLong(q.get("startblock")) : -1L;
            this.endBlock = q.containsKey("endblock") ? Long.parseLong(q.get("endblock")) : -1L;
            this.page = q.containsKey("page") ? Integer.parseInt(q.get("page")) : 1;
            this.offset = q.containsKey("offset") ? Integer.parseInt(q.get("offset")) : 10_000;
            this.chainId = q.get("chainid");
        }

        @Override
        public String toString() {
            return action + "[" + startBlock + ".." + endBlock + " p" + page + " o" + offset + "]";
        }
    }
}
