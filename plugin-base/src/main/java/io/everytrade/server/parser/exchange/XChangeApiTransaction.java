package io.everytrade.server.parser.exchange;

import com.univocity.parsers.common.DataValidationException;
import io.everytrade.server.model.Currency;
import io.everytrade.server.model.CurrencyPair;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.api.parser.FeeRebateImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.ImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.TransactionCluster;
import io.everytrade.server.plugin.impl.everytrade.parser.ParserUtils;
import io.everytrade.server.plugin.impl.everytrade.parser.exception.DataIgnoredException;
import io.everytrade.server.util.CoinMateDataUtil;
import lombok.Builder;
import lombok.Value;
import org.knowm.xchange.coinbase.v2.dto.account.transactions.CoinbaseShowTransactionV2;
import org.knowm.xchange.coinbase.v2.dto.account.transactions.CoinbaseTransactionV2Expand;
import org.knowm.xchange.coinmate.dto.trade.CoinmateTransactionHistoryEntry;
import org.knowm.xchange.dase.dto.account.ApiAccountTxn;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.account.FundingRecord;
import org.knowm.xchange.dto.trade.UserTrade;
import org.knowm.xchange.instrument.Instrument;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static io.everytrade.server.model.TransactionType.BUY;
import static io.everytrade.server.model.TransactionType.DEPOSIT;
import static io.everytrade.server.model.TransactionType.EARNING;
import static io.everytrade.server.model.TransactionType.FEE;
import static io.everytrade.server.model.TransactionType.REWARD;
import static io.everytrade.server.model.TransactionType.SELL;
import static io.everytrade.server.model.TransactionType.STAKING_REWARD;
import static io.everytrade.server.model.TransactionType.WITHDRAWAL;
import static io.everytrade.server.plugin.impl.everytrade.parser.ParserUtils.ROUNDING_MODE;
import static io.everytrade.server.plugin.impl.everytrade.parser.ParserUtils.nullOrZero;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.ExchangeBean.FEE_UID_PART;
import static io.everytrade.server.plugin.impl.everytrade.parser.exchange.ExchangeBean.UNSUPPORTED_TRANSACTION_TYPE;
import static java.util.Collections.emptyList;

@Value
@Builder
public class XChangeApiTransaction implements IXChangeApiTransaction {

    String id;
    Instant timestamp;
    TransactionType type;
    Currency base;
    Currency quote;
    Currency feeCurrency;
    BigDecimal originalAmount;
    BigDecimal price;
    BigDecimal feeAmount;
    String address;
    // the exchange's original transaction type when it differs from the mapped TransactionType
    // (e.g. QUICK_BUY -> BUY, BALANCE_MOVE_CREDIT -> DEPOSIT); persisted into the transaction note
    String note;
    @Builder.Default
    boolean logIgnoredFees = true;

    private static BigDecimal evalUnitPrice(BigDecimal quote, BigDecimal baseQuantity) {
        return quote.abs().divide(baseQuantity.abs(), ParserUtils.DECIMAL_DIGITS, ParserUtils.ROUNDING_MODE);
    }

    public static XChangeApiTransaction fromTrade(UserTrade trade) {
        final Instrument instrument = trade.getInstrument();
        if (!(instrument instanceof org.knowm.xchange.currency.CurrencyPair currencyPair)) {
            throw new DataValidationException("Derivatives are not supported yet.");
        }

        return XChangeApiTransaction.builder()
            .id(trade.getId())
            .timestamp(trade.getTimestamp().toInstant())
            .type(orderTypeToTxType(trade.getType()))
            .base(convert(currencyPair.getBase()))
            .quote(convert(currencyPair.getCounter()))
            .originalAmount(trade.getOriginalAmount())
            .price(trade.getPrice())
            .feeAmount(trade.getFeeAmount())
            .feeCurrency((trade.getFeeCurrency() == null) ? null : convert(trade.getFeeCurrency()))
            .build();
    }

    public static XChangeApiTransaction fromFunding(FundingRecord record) {
        var currency = convert(record.getCurrency());
        return XChangeApiTransaction.builder()
            .id(record.getInternalId())
            .timestamp(record.getDate().toInstant())
            .type(fundingTypeToTxType(record))
            .base(currency)
            .quote(currency)
            .originalAmount(record.getAmount())
            .feeAmount(record.getFee())
            .feeCurrency(currency)
            .address(record.getAddress())
            .build();
    }

    public static XChangeApiTransaction tradeCoinbase(List<CoinbaseShowTransactionV2> transaction) {
        var base = transaction.stream()
            .filter(t -> t.getAmount().getAmount().compareTo(BigDecimal.ZERO) > 0)
            .findFirst()
            .orElseThrow(() -> new DataValidationException("Trade transaction missing pair base"));
        var quote = transaction.stream()
            .filter(t -> t.getAmount().getAmount().compareTo(BigDecimal.ZERO) < 0)
            .findFirst()
            .orElseThrow(() -> new DataValidationException("Trade transaction missing pair quote"));

        return XChangeApiTransaction.builder()
            .id(String.valueOf(base.getId()))
            .timestamp(Instant.parse(base.getCreatedAt()))
            .type(BUY)
            .base(Currency.fromCode(base.getAmount().getCurrency()))
            .quote(Currency.fromCode(quote.getAmount().getCurrency()))
            .price(quote.getAmount().getAmount().abs().divide(base.getAmount().getAmount().abs(), 8, ROUNDING_MODE))
            .originalAmount(base.getAmount().getAmount())
            .build();
    }

    public static XChangeApiTransaction buySellCoinbase(CoinbaseShowTransactionV2 transaction) {
        TransactionType type = null;
        CoinbaseTransactionV2Expand buySellTx = null;
        switch (transaction.getType()) {
            case "buy" -> {
                type = BUY;
                buySellTx = transaction.getBuy();
                if (transaction.getAmount().getAmount().signum() < 0) {
                    throw new DataIgnoredException("Wallet transfer tx is ignored");
                }
            }
            case "sell" -> {
                type = SELL;
                buySellTx = transaction.getSell();
                if (transaction.getAmount().getAmount().signum() > 0) {
                    throw new DataIgnoredException("Wallet transfer tx is ignored");
                }
            }
        }

        if (buySellTx == null) {
            throw new DataValidationException("Coinbase transaction type not supported: " + transaction.getType());
        }

        Currency feeCurrency = null;
        if (buySellTx.getFee() != null) {
            feeCurrency = buySellTx.getFee().getCurrency() != null ? Currency.fromCode(buySellTx.getFee().getCurrency()) : null;
        }

        return XChangeApiTransaction.builder()
            .id(String.valueOf(transaction.getId()))
            .timestamp(Instant.parse(transaction.getCreatedAt()))
            .type(type)
            .originalAmount(transaction.getAmount().getAmount().abs())
            .base(Currency.fromCode(transaction.getAmount().getCurrency()))
            .quote(Currency.fromCode(buySellTx.getTotal().getCurrency()))
            .price(evalUnitPrice(buySellTx.getSubtotal().getAmount(), transaction.getAmount().getAmount()))
            .feeAmount(feeCurrency != null ? buySellTx.getFee().getAmount() : BigDecimal.ZERO)
            .feeCurrency(feeCurrency)
            .build();
    }

    public static XChangeApiTransaction depositWithdrawalCoinbase(CoinbaseShowTransactionV2 transaction) {
        TransactionType type = null;
        switch (transaction.getType()) {
            case "tx", "interest" -> type = REWARD;
            case "send" -> type = transaction.getAmount().getAmount().signum() > 0 ? DEPOSIT : WITHDRAWAL;
            case "earn_payout" -> type = EARNING;
            // Staking/inflation rewards = free crypto income (taxed at FMV on receipt). ETS-4965 import completeness.
            case "staking_reward", "inflation_reward" -> type = STAKING_REWARD;
            // NOTE: staking_transfer / unstaking_transfer (internal spot<->staked moves of an already-owned asset)
            // are intentionally NOT imported. The accounting engine pairs STAKE(-)/UNSTAKE(+); importing only one leg
            // inflates the position AND (per OrderedUnstakeProcessor's leftOver branch) re-stocks the asset at the
            // unstake date, resetting the time test. Leaving both unimported keeps the original BUY lot (cost + date)
            // intact. Staking rewards are captured separately above. See ETD-2134.
            case "fiat_withdrawal", "pro_withdrawal" -> type = WITHDRAWAL;
            case "fiat_deposit", "pro_deposit" -> type = DEPOSIT;
        }

        return XChangeApiTransaction.builder()
            .id(String.valueOf(transaction.getId()))
            .timestamp(Instant.parse(transaction.getCreatedAt()))
            .type(type)
            .base(Currency.fromCode(transaction.getAmount().getCurrency()))
            .quote(Currency.fromCode(transaction.getAmount().getCurrency()))
            .originalAmount(transaction.getAmount().getAmount().abs())
            .build();
    }

    public static XChangeApiTransaction fromCoinMateTransactions(CoinmateTransactionHistoryEntry transaction) {
        if (transaction == null) {
            throw new IllegalArgumentException("transaction must not be null");
        }

        CoinMateDataUtil.adaptTransactionStatus(transaction.getStatus());

        Currency base = Currency.fromCode(transaction.getAmountCurrency());

        Currency quote = null;
        String priceCurrency = transaction.getPriceCurrency();
        if (priceCurrency != null && !priceCurrency.isBlank()) {
            quote = Currency.fromCode(priceCurrency);
        }

        Currency feeCurrency = null;
        String feeCurrencyStr = transaction.getFeeCurrency();
        if (feeCurrencyStr != null && !feeCurrencyStr.isBlank()) {
            feeCurrency = Currency.fromCode(feeCurrencyStr);
        }

        var type = CoinMateDataUtil.mapCoinMateType(transaction.getTransactionType());
        String originalType = transaction.getTransactionType();

        var amount = transaction.getAmount();
        if (amount != null) {
            amount = amount.abs();
        }

        var feeAmount = transaction.getFee();
        if (feeAmount != null) {
            feeAmount = feeAmount.abs();
        }

        return XChangeApiTransaction.builder()
            .id(String.valueOf(transaction.getTransactionId()))
            .timestamp(Instant.ofEpochMilli(transaction.getTimestamp()))
            .type(type)
            .price(transaction.getPrice())
            .base(base)
            .quote(quote)
            .originalAmount(amount)
            .feeAmount(feeAmount)
            .feeCurrency(feeCurrency)
            .note(type.name().equalsIgnoreCase(originalType) ? null : originalType)
            .build();
    }

    public static TransactionCluster tradeFillGroupToCluster(List<ApiAccountTxn> group) {
        long createdAt = group.stream()
            .map(ApiAccountTxn::getCreatedAt)
            .findFirst()
            .orElseThrow(() ->
                new IllegalStateException("Missing createdAt in trade fill group: " + group)
            );

        ApiAccountTxn baseLeg = group.stream()
            .filter(t -> "trade_fill_credit_base".equals(t.getTxnType()) || "trade_fill_debit_base".equals(t.getTxnType()))
            .findFirst()
            .orElse(null);

        ApiAccountTxn quoteLeg = group.stream()
            .filter(t -> "trade_fill_credit_quote".equals(t.getTxnType()) || "trade_fill_debit_quote".equals(t.getTxnType()))
            .findFirst()
            .orElse(null);

        if (baseLeg == null || quoteLeg == null) {
            return null;
        }

        BigDecimal baseAmt = baseLeg.getAmount();
        BigDecimal quoteAmt = quoteLeg.getAmount();

        if (baseAmt == null || quoteAmt == null) {
            throw new IllegalStateException("Missing amount in trade fill group: " + group);
        }

        boolean buy = baseAmt.signum() > 0 || quoteAmt.signum() < 0;

        Currency base = Currency.fromCode(baseLeg.getCurrency());
        Currency quote = Currency.fromCode(quoteLeg.getCurrency());

        // ParserUtils.DECIMAL_DIGITS (17) is the host's price scale: a finer quotient is rounded a second time when
        // stored, and the host's rebuilt quote total then misses the traded amount (73.84000000000000001 CZK).
        BigDecimal price = quoteAmt.abs().divide(baseAmt.abs(), ParserUtils.DECIMAL_DIGITS, ParserUtils.ROUNDING_MODE);

        String tradeId = baseLeg.getTradeId() != null ? baseLeg.getTradeId() : quoteLeg.getTradeId();
        Instant ts = Instant.ofEpochMilli(createdAt);

        XChangeApiTransaction x = XChangeApiTransaction.builder()
            .id(tradeId != null ? tradeId : baseLeg.getId())
            .timestamp(ts)
            .type(buy ? TransactionType.BUY : TransactionType.SELL)
            .base(base)
            .quote(quote)
            .originalAmount(baseAmt.abs())
            .price(price)
            .build();

        return x.toTransactionCluster();
    }

    /**
     * DASE types that move money between the customer's OWN DASE portfolios: {@code portfolio_transfer_*} is how
     * the Trading portfolio names them, {@code transfer_*} how the Basic (instant-buy) portfolio does.
     */
    public static final Set<String> DASE_TRANSFER_TYPES = Set.of(
        "transfer_credit", "transfer_debit", "portfolio_transfer_credit", "portfolio_transfer_debit"
    );

    /**
     * A one-off balance correction DASE books under its own Funding ID, with no counter-leg (Basic portfolio export
     * only; not a type of the DASE API). DASE does not document what it stands for: the one measured row is the credit
     * of a replacement token, but a compensation or a correction looks the same. It is not something the customer
     * executed, so it is never booked; every such row is reported as ignored with
     * {@value #DASE_ADJUSTMENT_NOT_IMPORTED}.
     */
    public static final String DASE_ADJUSTMENT = "adjustment";

    /** Message of the ignored problem every {@value #DASE_ADJUSTMENT} row produces (no semicolon). */
    public static final String DASE_ADJUSTMENT_NOT_IMPORTED =
        "DASE adjustment not imported - book it manually (replacement token, compensation or correction)";

    /**
     * Message of the ignored problem a DASE funding group of withdrawal block / unblock rows only produces. The rows
     * alone cannot say WHY nothing completed: a cancelled withdrawal (block + unblock), a pending one that was
     * re-blocked (block, unblock, block), and - on the API path - a lifecycle split across sync runs whose
     * completing row was imported in another run all look the same, so the message claims none of them.
     */
    public static final String DASE_WITHDRAWAL_NOT_COMPLETED =
        "DASE withdrawal without a completing row (cancelled, or completed/pending outside this import)";

    /**
     * An official DASE API type: funds of a withdrawal returned to the account. It is NOT booked yet (booking it is
     * an owner decision); until then every such row is reported as ignored with
     * {@value #DASE_WITHDRAWAL_REFUND_NOT_IMPORTED} - neither an error, which it is not, nor silently dropped.
     */
    public static final String DASE_WITHDRAWAL_REFUND = "withdrawal_refund";

    /** Message of the ignored problem every {@value #DASE_WITHDRAWAL_REFUND} row produces. */
    public static final String DASE_WITHDRAWAL_REFUND_NOT_IMPORTED = "DASE withdrawal_refund is not imported yet";

    private static final String DASE_DEPOSIT = "deposit";
    private static final String DASE_WITHDRAWAL_BLOCK = "withdrawal_block";
    private static final String DASE_WITHDRAWAL_UNBLOCK = "withdrawal_unblock";
    /** The completing row of a withdrawal: {@code withdrawal_commit} in the Trading portfolio, plain
     * {@code withdrawal} in the Basic one. They play exactly the same role. */
    private static final Set<String> DASE_WITHDRAWAL_COMPLETIONS = Set.of("withdrawal_commit", "withdrawal");

    /**
     * The row of a DASE funding group that the group is imported as.
     * <p>
     * Pick the row that MOVED something, not whatever happens to come first. A DASE withdrawal arrives as a
     * three-row lifecycle sharing one Funding ID - withdrawal_block (-X), withdrawal_unblock (+X) and
     * withdrawal_commit (-X, the real movement) - and a cancelled one as just block + unblock, which nets to
     * zero. Those rows land in ONE group, so taking the first row meant that whenever the iteration order put a
     * block or an unblock in front, the whole group was ignored and the customer's withdrawal silently
     * disappeared. MEASURED on a 2 199-row export of 2026-10-12: 8 of 16 withdrawals were lost that way, and
     * because it depends on row order it was never reproducible on demand.
     * <p>
     * Order of preference: the first deposit or completing withdrawal row; else the first row that is not a
     * withdrawal lifecycle row (block, unblock, {@value #DASE_WITHDRAWAL_REFUND}); else the first row (a group of
     * lifecycle rows only). Within each tier a row WITH a date wins over one without, so that one dateless extra
     * row cannot take the place of - and fail - a valid dated row (the caller reports the dateless one as an
     * unexpected row instead).
     */
    public static ApiAccountTxn daseChosenFundingRow(List<ApiAccountTxn> group) {
        return firstPreferringDated(group, x -> DASE_DEPOSIT.equals(x.getTxnType())
                || isDaseWithdrawalCompletion(x.getTxnType()))
            .or(() -> firstPreferringDated(group, x -> !isDaseLifecycleRow(x)))
            .orElseGet(() -> firstPreferringDated(group, x -> true).orElse(null));
    }

    private static java.util.Optional<ApiAccountTxn> firstPreferringDated(List<ApiAccountTxn> group,
                                                                       java.util.function.Predicate<ApiAccountTxn> p) {
        final List<ApiAccountTxn> candidates = group.stream().filter(Objects::nonNull).filter(p).toList();
        return candidates.stream().filter(x -> x.getCreatedAt() != 0L).findFirst()
            .or(() -> candidates.stream().findFirst());
    }

    /**
     * The rows of a DASE funding group (rows sharing a Funding ID, or a dase TX ID when there is no Funding ID)
     * that {@link #fundingOrSingleTxnToCluster} would NOT account for: everything except the chosen row
     * ({@link #daseChosenFundingRow}) and the withdrawal block / unblock / refund rows of its lifecycle (the caller
     * reports each refund row separately, see {@link #DASE_WITHDRAWAL_REFUND}). MEASURED on both
     * customer exports of 2026-10-12 (Trading 2 199 rows, Basic 399 rows): no group has such a row. A group is
     * mapped to ONE cluster, so without this every such row - a second deposit under the same Funding ID, a row
     * type DASE introduces later - would vanish without a trace; the caller reports each one as its own problem.
     */
    public static List<ApiAccountTxn> daseUnexpectedFundingRows(List<ApiAccountTxn> group) {
        final ApiAccountTxn chosen = daseChosenFundingRow(group);
        return group.stream()
            .filter(Objects::nonNull)
            .filter(x -> x != chosen && !isDaseLifecycleRow(x))
            .toList();
    }

    /** Message for a row {@link #daseUnexpectedFundingRows} returned (no semicolon, no internal names). */
    public static String daseUnexpectedFundingRowMessage(List<ApiAccountTxn> group, ApiAccountTxn unexpected) {
        final ApiAccountTxn chosen = daseChosenFundingRow(group);
        return String.format("DASE funding group of %d rows has an unexpected %s row next to its %s row",
            group.size(), unexpected.getTxnType(), chosen == null ? null : chosen.getTxnType());
    }

    /** Null-safe: {@code Set.of(...).contains(null)} throws. */
    private static boolean isDaseWithdrawalCompletion(String txnType) {
        return txnType != null && DASE_WITHDRAWAL_COMPLETIONS.contains(txnType);
    }

    private static boolean isDaseBlockOrUnblock(ApiAccountTxn t) {
        return DASE_WITHDRAWAL_BLOCK.equals(t.getTxnType()) || DASE_WITHDRAWAL_UNBLOCK.equals(t.getTxnType());
    }

    /** True for a {@value #DASE_WITHDRAWAL_REFUND} row. Null-safe. */
    public static boolean isDaseWithdrawalRefund(ApiAccountTxn t) {
        return t != null && DASE_WITHDRAWAL_REFUND.equals(t.getTxnType());
    }

    /** Withdrawal rows that only document the lifecycle and are never imported as the group's movement. */
    private static boolean isDaseLifecycleRow(ApiAccountTxn t) {
        return isDaseBlockOrUnblock(t) || isDaseWithdrawalRefund(t);
    }

    /**
     * Official DASE API types of the fee a Trading trade paid, booked under the trade's Trade ID next to its base
     * and quote legs. NOT booked yet: {@link #tradeFillGroupToCluster} has always ignored them, and booking them
     * changes the customer's numbers (an owner decision of its own). Both the CSV import and the API connector report
     * every fee row as ignored ({@value #DASE_TRADE_FEE_NOT_IMPORTED}) so that it is visible instead of silently
     * dropped.
     */
    public static final Set<String> DASE_TRADE_FEE_TYPES = Set.of("trade_fill_fee_base", "trade_fill_fee_quote");

    /** Message of the ignored problem every DASE trade fee row ({@link #DASE_TRADE_FEE_TYPES}) produces. */
    public static final String DASE_TRADE_FEE_NOT_IMPORTED = "DASE trade fee is not imported yet";

    /**
     * Every row type the DASE mapping knows: the 13 values of the API's {@code AccountTxnType} enum
     * (https://api.dase.com/docs) plus the types only the Basic-portfolio CSV export writes.
     */
    public static final Set<String> DASE_KNOWN_TYPES = Set.of(
        "deposit", "withdrawal_block", "withdrawal_unblock", "withdrawal_commit", "withdrawal_refund",
        "trade_fill_credit_base", "trade_fill_debit_base", "trade_fill_credit_quote", "trade_fill_debit_quote",
        "trade_fill_fee_base", "trade_fill_fee_quote", "portfolio_transfer_credit", "portfolio_transfer_debit",
        "withdrawal", "transfer_credit", "transfer_debit", DASE_ADJUSTMENT, "trade payment", "trade fill",
        "trade refund"
    );

    /** True for a row with a type that is not blank and not one of {@link #DASE_KNOWN_TYPES}. Null-safe. */
    public static boolean isDaseUnknownType(ApiAccountTxn t) {
        return t != null && t.getTxnType() != null && !t.getTxnType().isBlank()
            && !DASE_KNOWN_TYPES.contains(t.getTxnType());
    }

    /** True for a DASE trade fee row ({@link #DASE_TRADE_FEE_TYPES}). Null-safe. */
    public static boolean isDaseTradeFee(ApiAccountTxn t) {
        return t != null && t.getTxnType() != null && DASE_TRADE_FEE_TYPES.contains(t.getTxnType());
    }

    /**
     * Null when a DASE Trading trade group ({@code trade_fill_*} rows sharing a Trade ID) is exactly one base leg
     * and one quote leg plus any number of fee legs ({@link #DASE_TRADE_FEE_TYPES}), else the message describing
     * what it is. On the CSV path such a group fails (see
     * {@code XChangeConnectorParser.DaseMappingSink#isCompleteFile}); on the API path {@link #tradeFillGroupToCluster}
     * ignores extra legs and a group with a missing leg is reported as ignored with this message.
     */
    public static String daseTradeGroupShapeProblem(List<ApiAccountTxn> group) {
        final List<ApiAccountTxn> rows = group.stream().filter(Objects::nonNull).toList();
        final long base = rows.stream().filter(t -> isDaseTradeLeg(t, "base")).count();
        final long quote = rows.stream().filter(t -> isDaseTradeLeg(t, "quote")).count();
        final long fee = rows.stream().filter(XChangeApiTransaction::isDaseTradeFee).count();
        if (base == 1 && quote == 1 && rows.size() == 2 + fee) {
            return null;
        }
        return "DASE trade group must have exactly one base and one quote leg, got "
            + rows.stream().map(t -> String.valueOf(t.getTxnType())).collect(java.util.stream.Collectors.joining(", "));
    }

    private static boolean isDaseTradeLeg(ApiAccountTxn t, String side) {
        return ("trade_fill_credit_" + side).equals(t.getTxnType()) || ("trade_fill_debit_" + side).equals(t.getTxnType());
    }

    /**
     * Maps one DASE funding group - every row sharing a Funding ID - or a single row to a cluster. The caller
     * removes the {@value #DASE_WITHDRAWAL_REFUND} rows and the rows {@link #daseUnexpectedFundingRows} reports
     * first; what is left is the chosen row ({@link #daseChosenFundingRow}) plus any withdrawal block / unblock
     * rows, which only document the lifecycle.
     * <ul>
     *     <li>{@code deposit} -> DEPOSIT, id = Funding ID. The amount must be positive.</li>
     *     <li>{@code withdrawal_commit} (Trading portfolio) and {@code withdrawal} (Basic portfolio) -> WITHDRAWAL,
     *     id = Funding ID. The amount must be negative. A group of withdrawal block / unblock rows only completed
     *     nothing in this import and is ignored ({@value #DASE_WITHDRAWAL_NOT_COMPLETED}).</li>
     *     <li>{@code transfer_credit}, {@code portfolio_transfer_credit} -> DEPOSIT (amount must be positive) and
     *     {@code transfer_debit}, {@code portfolio_transfer_debit} -> WITHDRAWAL (amount must be negative),
     *     id = Funding ID + ':' + type. The two legs of one transfer between the customer's own portfolios carry
     *     the SAME Funding ID (one leg in each portfolio's export), and the suffix keeps their external ids
     *     distinct. The host does NOT de-duplicate on import - every imported row is stored, whatever its uid - but
     *     its duplicate check (Inspect / Analysis) flags rows of one container that share an external id, which is
     *     what the bare Funding ID would trigger if both exports were ever imported into one container. Deposits
     *     and withdrawals keep the bare Funding ID, so the external ids of rows already imported stay as they
     *     were.</li>
     *     <li>{@code adjustment}: never booked, reported as ignored ({@value #DASE_ADJUSTMENT_NOT_IMPORTED}), whatever
     *     its currency or sign (see {@link #DASE_ADJUSTMENT}).</li>
     * </ul>
     * For the three booked kinds an amount of zero, or no amount, moved nothing and is ignored (on both paths).
     * <p>
     * The "must be positive / negative" rules above hold only when {@code validateSigns} is true - the CSV import.
     * There an amount whose sign contradicts the row type is a validation error: importing its absolute value would
     * book the movement in the wrong direction. MEASURED on both customer exports of 2026-10-12 (2 599 rows): no
     * deposit, completing withdrawal or transfer row has a zero or contradicting amount, so the rule changes
     * nothing there. The sign of the API {@code amount} was never measured, so the API connector passes
     * {@code false} and keeps the behaviour it always had: the direction comes from the type name and the amount
     * is taken as its absolute value.
     * <p>
     * A row without a type or without any id (neither Funding ID nor dase TX ID) is an error, not an ignored row:
     * nothing can be said about what it moved, and an id-less transaction cannot be told apart from another one.
     * Transfer and adjustment rows must reach this method one row per call (see
     * {@code XChangeConnectorParser.daseTransactionCluster}); a group is mapped to ONE cluster.
     */
    public static TransactionCluster fundingOrSingleTxnToCluster(List<ApiAccountTxn> group, boolean validateSigns) {
        final ApiAccountTxn t = daseChosenFundingRow(group);
        if (t == null) {
            return null;
        }
        final String txnType = t.getTxnType();
        if (txnType == null || txnType.isBlank()) {
            throw new IllegalStateException("DASE row has no type");
        }
        final String fundingOrTxnId = t.getFundingId() != null ? t.getFundingId() : t.getId();
        if (fundingOrTxnId == null || fundingOrTxnId.isBlank()) {
            throw new IllegalStateException("DASE row has no id");
        }

        String id = fundingOrTxnId;
        final TransactionType type;
        if (DASE_TRANSFER_TYPES.contains(txnType)) {
            final boolean credit = txnType.endsWith("_credit");
            requireDaseSign(t, credit, "DASE transfer with zero amount", validateSigns);
            type = credit ? TransactionType.DEPOSIT : TransactionType.WITHDRAWAL;
            id = fundingOrTxnId + ":" + txnType;
        } else if (DASE_ADJUSTMENT.equals(txnType)) {
            throw new DataIgnoredException(DASE_ADJUSTMENT_NOT_IMPORTED);
        } else if (DASE_DEPOSIT.equals(txnType)) {
            requireDaseSign(t, true, "DASE deposit with zero amount", validateSigns);
            type = TransactionType.DEPOSIT;
        } else if (isDaseWithdrawalCompletion(txnType)) {
            requireDaseSign(t, false, "DASE withdrawal with zero amount", validateSigns);
            type = TransactionType.WITHDRAWAL;
        } else if (isDaseWithdrawalWithoutCompletion(group)) {
            throw new DataIgnoredException(DASE_WITHDRAWAL_NOT_COMPLETED);
        } else {
            throw new DataIgnoredException(UNSUPPORTED_TRANSACTION_TYPE + txnType);
        }

        Instant ts = Instant.ofEpochMilli(t.getCreatedAt());
        Currency ccy = Currency.fromCode(t.getCurrency());

        XChangeApiTransaction x = XChangeApiTransaction.builder()
            .id(id)
            .timestamp(ts)
            .type(type)
            .base(ccy)
            .quote(ccy)
            .originalAmount(t.getAmount().abs())
            .build();

        return x.toTransactionCluster();
    }

    /**
     * Zero or no amount: ignored with {@code zeroMessage}. A sign that contradicts the row type: validation error,
     * but only when {@code validateSign} (CSV path, see {@link #fundingOrSingleTxnToCluster}).
     */
    private static void requireDaseSign(ApiAccountTxn t, boolean positive, String zeroMessage, boolean validateSign) {
        final BigDecimal amount = t.getAmount();
        if (amount == null || amount.signum() == 0) {
            throw new DataIgnoredException(zeroMessage);
        }
        if (validateSign && (amount.signum() > 0) != positive) {
            throw new DataValidationException(String.format("DASE %s must have a %s amount, got %s",
                t.getTxnType(), positive ? "positive" : "negative", amount.toPlainString()));
        }
    }

    /** At least one row and only withdrawal block / unblock rows: nothing completed in this import. */
    private static boolean isDaseWithdrawalWithoutCompletion(List<ApiAccountTxn> group) {
        final List<ApiAccountTxn> rows = group.stream().filter(Objects::nonNull).toList();
        return !rows.isEmpty() && rows.stream().allMatch(XChangeApiTransaction::isDaseBlockOrUnblock);
    }

    /**
     * Maps one FILLED order of the DASE Basic (instant-buy) portfolio - exactly one {@code trade payment} (the
     * paid leg, negative) and one {@code trade fill} (the received leg, positive) sharing an Order ID - to a
     * BUY or SELL.
     * <p>
     * Paid in fiat, received crypto -> BUY of the crypto; paid crypto, received fiat -> SELL of the crypto. The
     * volume is the crypto amount, the unit price fiat / crypto at the scale {@link #tradeFillGroupToCluster} uses,
     * the timestamp the fill's and the id the Order ID (numeric at DASE, so it cannot collide with a Trading
     * portfolio Trade ID, which is a UUID). No fee row: the DASE margin is inside the rate.
     * <p>
     * Why this takes plain values instead of {@link ApiAccountTxn}s like its Trading sibling: the Basic legs are
     * joined by Order ID, a column {@link ApiAccountTxn} (external xchange-dase library) does not carry. The
     * grouping and the shape checks therefore live with the CSV reader that has the Order ID
     * ({@code parser.exchange.dase.DaseBasicOrders}); only the interpretation of a paid/received pair lives here,
     * next to the Trading one, so an API path that ever delivers Basic orders can reuse it.
     *
     * @throws DataValidationException when the pair is not exactly one fiat and one crypto leg, or the signs are
     *                                 not paid &lt; 0 &lt; received
     */
    public static TransactionCluster daseOrderToCluster(String orderId,
                                                        Instant executed,
                                                        String paidCurrency,
                                                        BigDecimal paidAmount,
                                                        String receivedCurrency,
                                                        BigDecimal receivedAmount) {
        if (paidAmount == null || receivedAmount == null || paidAmount.signum() >= 0 || receivedAmount.signum() <= 0) {
            throw new DataValidationException(String.format(
                "DASE order %s: expected a negative trade payment and a positive trade fill, got %s %s and %s %s",
                orderId, paidAmount, paidCurrency, receivedAmount, receivedCurrency));
        }
        final Currency paid = Currency.fromCode(paidCurrency);
        final Currency received = Currency.fromCode(receivedCurrency);

        final boolean buy;
        if (paid.isFiat() && !received.isFiat()) {
            buy = true;
        } else if (!paid.isFiat() && received.isFiat()) {
            buy = false;
        } else {
            throw new DataValidationException(String.format(
                "DASE order %s: only fiat-for-crypto or crypto-for-fiat is supported, got %s for %s",
                orderId, paid.code(), received.code()));
        }

        final Currency crypto = buy ? received : paid;
        final Currency fiat = buy ? paid : received;
        final BigDecimal cryptoAmount = (buy ? receivedAmount : paidAmount).abs();
        final BigDecimal fiatAmount = (buy ? paidAmount : receivedAmount).abs();

        return XChangeApiTransaction.builder()
            .id(orderId)
            .timestamp(executed)
            .type(buy ? BUY : SELL)
            .base(crypto)
            .quote(fiat)
            .originalAmount(cryptoAmount)
            .price(fiatAmount.divide(cryptoAmount, ParserUtils.DECIMAL_DIGITS, ParserUtils.ROUNDING_MODE))
            .build()
            .toTransactionCluster();
    }

    public TransactionCluster toTransactionCluster() {
        boolean isFailedFee = false;
        final boolean isIgnoredFee = (feeCurrency == null && !nullOrZero(feeAmount));
        String failedFeeMessage = "";

        if (type.isBuyOrSell()) {
            try {
                new CurrencyPair(base, quote);
            } catch (CurrencyPair.FiatCryptoCombinationException e) {
                throw new DataValidationException(e.getMessage());
            }
        }

        List<ImportedTransactionBean> related;
        if (nullOrZero(feeAmount) || isIgnoredFee) {
            related = emptyList();
        } else {
            try {
                related = List.of(new FeeRebateImportedTransactionBean(
                        id + FEE_UID_PART,
                        timestamp,
                        feeCurrency,
                        feeCurrency,
                        FEE,
                        feeAmount,
                        feeCurrency
                    )
                );
            } catch (Exception e) {
                isFailedFee = true;
                failedFeeMessage = e.getMessage();
                related = emptyList();
            }
        }

        TransactionCluster cluster;
        if (type.isDepositOrWithdrawal()) {
            cluster = createDepositWithdrawalTx(related);
        } else if (type.isBuyOrSell() || type.isZeroCostGain() || type.isStaking()) {
            cluster = createTx(related);
        } else {
            throw new DataValidationException("Unsupported type " + type.name());
        }

        if (isIgnoredFee && logIgnoredFees) {
            cluster.setIgnoredFee(1, "Fee " + (feeCurrency != null ? feeCurrency.code() : "null") + " currency is not base or quote");
        }
        if (isFailedFee) {
            cluster.setFailedFee(1, String.format("Fee transaction failed - %s", failedFeeMessage));
        }
        return cluster;
    }

    private TransactionCluster createDepositWithdrawalTx(List<ImportedTransactionBean> related) {
        return new TransactionCluster(
            ImportedTransactionBean.createDepositWithdrawal(
                id,
                timestamp,
                base,
                quote,
                type,
                originalAmount,
                address,
                note,
                null
            ),
            related
        );
    }

    private TransactionCluster createTx(List<ImportedTransactionBean> related) {
        return new TransactionCluster(
            new ImportedTransactionBean(
                id,
                timestamp,
                base,
                quote,
                type,
                originalAmount,
                price,
                note,
                address
            ),
            related
        );
    }

    private static TransactionType orderTypeToTxType(Order.OrderType orderType) {
        return switch (orderType) {
            case ASK -> TransactionType.SELL;
            case BID -> TransactionType.BUY;
            default -> throw new DataValidationException("ExchangeBean.UNSUPPORTED_TRANSACTION_TYPE ".concat(orderType.name()));
        };
    }

    private static TransactionType fundingTypeToTxType(FundingRecord record) {
        if (record.getDescription() != null) {
            String desc = record.getDescription().toLowerCase();

            if (desc.equals("withdrawal_block") || desc.equals("withdrawal_unblock")) {
                throw new DataIgnoredException(
                    "Ignored transaction: " + record.getDescription());
            }
        }

        switch (record.getType()) {
            case WITHDRAWAL:
                return WITHDRAWAL;
            case DEPOSIT:
                return DEPOSIT;
            case OTHER_INFLOW:
                // TODO this is binance specific - move it elsewhere
                if (isAirdrop(record)) {
                    return TransactionType.AIRDROP;
                }
                if (isStake(record)) {
                    return TransactionType.STAKE;
                }
                if (isUnstake(record)) {
                    return TransactionType.UNSTAKE;
                }
                if (isStakingReward(record)) {
                    return TransactionType.STAKING_REWARD;
                }
        }

        throw new DataValidationException(
            UNSUPPORTED_TRANSACTION_TYPE + record.getType().name());
    }

    private static Currency convert(org.knowm.xchange.currency.Currency currency) {
        try {
            return Currency.fromCode(currency.getCurrencyCode());
        } catch (IllegalArgumentException e) {
            final org.knowm.xchange.currency.Currency currencyConverted =
                org.knowm.xchange.currency.Currency.getInstance(currency.getCurrencyCode()).getCommonlyUsedCurrency();
            return Currency.fromCode(currencyConverted.getCurrencyCode());
        }
    }

    private static boolean isAirdrop(FundingRecord r) {
        return r.getDescription() != null && r.getDescription().toLowerCase().endsWith("airdrop");
    }

    private static boolean isStake(FundingRecord r) {
        return r.getDescription() != null && r.getDescription().toLowerCase().startsWith("staking");
    }

    private static boolean isUnstake(FundingRecord r) {
        return false; // TODO dont know how this looks in API
    }

    private static boolean isStakingReward(FundingRecord r) {
        return r.getDescription() != null && r.getDescription().toLowerCase().endsWith("distribution");
    }

}
