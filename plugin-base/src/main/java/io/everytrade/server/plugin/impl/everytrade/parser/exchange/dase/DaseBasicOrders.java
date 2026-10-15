package io.everytrade.server.plugin.impl.everytrade.parser.exchange.dase;

import io.everytrade.server.parser.exchange.XChangeApiTransaction;
import io.everytrade.server.plugin.api.parser.TransactionCluster;
import io.everytrade.server.plugin.impl.everytrade.parser.exception.DataIgnoredException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Reads the orders of a DASE Basic (instant-buy) portfolio export.
 * <p>
 * A Basic order is written as rows of three types sharing one numeric {@code Order ID} (its {@code Trade ID}
 * is empty):
 * <ul>
 *     <li>{@code trade payment} - the paid leg, always negative;</li>
 *     <li>{@code trade fill} - the received leg, positive;</li>
 *     <li>{@code trade refund} - the paid amount returned when the order was cancelled.</li>
 * </ul>
 * A filled order is exactly one payment and one fill; a cancelled order is one payment and one refund of the
 * same amount and currency, and moved nothing. MEASURED on a customer's Basic export (2026-10-12): 158 orders,
 * 147 filled (108 bought crypto for CZK, 39 sold crypto for CZK) and 11 cancelled - no other shape. A payment with
 * neither a fill nor a refund (an order still open when the export was made) was not executed and is ignored with
 * that reason, like a cancelled one. Anything else is reported as a parsing error rather than guessed at, so a
 * shape DASE introduces later shows up in the import log instead of as a silently missing or wrong trade.
 * <p>
 * Only these three types are grouped by Order ID. Funding rows (deposit, withdrawal*, transfer*, adjustment)
 * never are, even when they carry an Order ID: MEASURED, one CZK withdrawal (block, unblock, withdrawal) carried
 * the Order ID of the SELL whose proceeds it withdrew, and grouping by Order ID would have fused the two into one
 * five-row "order". Funding rows are grouped by Funding ID in the shared DASE mapping.
 */
public final class DaseBasicOrders {

    static final String PAYMENT = "trade payment";
    static final String FILL = "trade fill";
    static final String REFUND = "trade refund";

    private static final Set<String> ORDER_ROW_TYPES = Set.of(PAYMENT, FILL, REFUND);

    public static final String CANCELLED_MESSAGE = "DASE order cancelled and refunded";

    public static final String NOT_EXECUTED_MESSAGE = "DASE order not executed (a payment without a fill or refund)";

    private DaseBasicOrders() {
    }

    public static boolean isOrderRowType(String type) {
        return type != null && ORDER_ROW_TYPES.contains(type);
    }

    /**
     * Groups order rows by Order ID (file order kept) and maps each order to a cluster, or to a bean that reports
     * why it was not imported. Rows without an Order ID are reported one by one.
     */
    public static List<DaseBeanV1> toBeans(List<DaseBeanV1> orderRows) {
        final Map<String, List<DaseBeanV1>> orders = new LinkedHashMap<>();
        final List<DaseBeanV1> result = new ArrayList<>();
        for (DaseBeanV1 row : orderRows) {
            if (row.getOrderId() == null) {
                result.add(DaseBeanV1.failingRows(List.of(row), "DASE " + row.getType() + " row without Order ID",
                    false));
                continue;
            }
            orders.computeIfAbsent(row.getOrderId(), k -> new ArrayList<>()).add(row);
        }
        for (Map.Entry<String, List<DaseBeanV1>> order : orders.entrySet()) {
            try {
                result.add(DaseBeanV1.carrying(toCluster(order.getKey(), order.getValue()), order.getValue()));
            } catch (DataIgnoredException e) {
                result.add(DaseBeanV1.failingRows(order.getValue(), e.getMessage(), true));
            } catch (Exception e) {
                final String reason = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                final String message = reason.startsWith("DASE order")
                    ? reason
                    : "DASE order " + order.getKey() + ": " + reason;
                result.add(DaseBeanV1.failingRows(order.getValue(), message, false));
            }
        }
        return result;
    }

    /**
     * One order to one cluster.
     *
     * @throws DataIgnoredException  for a cancelled order (payment + full refund, no fill) and for an order not
     *                               executed when the export was made (one payment, no fill, no refund); raised
     *                               BEFORE any currency is resolved, so such an order in a currency Whalebooks does
     *                               not know is ignored, not failed
     * @throws IllegalStateException for any other shape than one payment + one fill
     */
    static TransactionCluster toCluster(String orderId, List<DaseBeanV1> rows) {
        final List<DaseBeanV1> payments = ofType(rows, PAYMENT);
        final List<DaseBeanV1> fills = ofType(rows, FILL);
        final List<DaseBeanV1> refunds = ofType(rows, REFUND);

        if (payments.size() == 1 && fills.isEmpty() && refunds.size() == 1
            && isFullRefund(payments.get(0), refunds.get(0))) {
            throw new DataIgnoredException(CANCELLED_MESSAGE);
        }
        if (payments.size() == 1 && fills.isEmpty() && refunds.isEmpty()) {
            // Owner policy of 2026-10-14: import only what was executed. None measured in the two customer exports.
            throw new DataIgnoredException(NOT_EXECUTED_MESSAGE);
        }
        if (payments.size() != 1 || fills.size() != 1 || !refunds.isEmpty()) {
            throw new IllegalStateException(String.format(
                "DASE order %s has an unsupported shape: %d trade payment, %d trade fill, %d trade refund rows "
                    + "(expected one payment with one fill, or one payment with a full refund)",
                orderId, payments.size(), fills.size(), refunds.size()));
        }

        final DaseBeanV1 payment = payments.get(0);
        final DaseBeanV1 fill = fills.get(0);
        if (fill.getDate() == null) {
            throw new IllegalStateException("DASE order " + orderId + ": trade fill row has no date");
        }
        return XChangeApiTransaction.daseOrderToCluster(
            orderId,
            fill.getDate(),
            payment.getCurrency(),
            payment.getAmount(),
            fill.getCurrency(),
            fill.getAmount()
        );
    }

    private static boolean isFullRefund(DaseBeanV1 payment, DaseBeanV1 refund) {
        final BigDecimal paid = payment.getAmount();
        final BigDecimal returned = refund.getAmount();
        return paid != null && returned != null
            && paid.signum() < 0
            && paid.negate().compareTo(returned) == 0
            && Objects.equals(payment.getCurrency(), refund.getCurrency());
    }

    private static List<DaseBeanV1> ofType(List<DaseBeanV1> rows, String type) {
        return rows.stream().filter(r -> type.equals(r.getType())).toList();
    }
}
