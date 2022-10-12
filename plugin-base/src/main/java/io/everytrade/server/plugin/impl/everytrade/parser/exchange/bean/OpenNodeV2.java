package io.everytrade.server.plugin.impl.everytrade.parser.exchange.bean;

import com.univocity.parsers.annotations.Parsed;
import com.univocity.parsers.common.DataValidationException;
import io.everytrade.server.model.Currency;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.api.parser.FeeRebateImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.ImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.TransactionCluster;
import io.everytrade.server.plugin.impl.everytrade.parser.ParserUtils;
import io.everytrade.server.plugin.impl.everytrade.parser.exception.DataIgnoredException;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.ExchangeBean;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static io.everytrade.server.model.TransactionType.FEE;
import static io.everytrade.server.model.TransactionType.WITHDRAWAL;
import static io.everytrade.server.plugin.impl.everytrade.parser.ParserUtils.nullOrZero;
import static java.util.Collections.emptyList;
import static lombok.AccessLevel.PRIVATE;

@FieldDefaults(level = PRIVATE)
public class OpenNodeV2 extends ExchangeBean {

    String openNodeID;
    String typeOfTransfer;
    String statusOfTransfer;
    String date;
    String time;
    String amount;
    String transferFeesPaid;
    String currency;

    @Parsed(field = "OpenNode ID")
    public void setOrderNodeId(String s) {
        this.openNodeID = s;
    }

    @Parsed(field = "Type of transfer")
    public void setTypeOfTransfer(String s) {
        typeOfTransfer = s;
    }

    @Parsed(field = "Status of transfer")
    public void setStatusOfTransfer(String s) {
        statusOfTransfer = s;
    }

    @Parsed(field = "Date (mm/dd/yyyy UTC)")
    public void setDate(String value) {
        date = value;
    }

    @Parsed(field = "Time (UTC)")
    public void setTime(String value) {
        time = value;
    }

    @Parsed(field = "Amount")
    public void setAmount(String s) {
        amount = s;
    }

    @Parsed(field = "Transfer fees paid")
    public void setTransferFeesPaid(String s) {
        transferFeesPaid = s;
    }

    @Parsed(field = "Currency")
    public void setFeeCurrency(String s) {
        currency = s;
    }

    private Instant combineDateAndTime(String day, String time) {
        try {
            String stringDate = String.format("%s %s", day, time);
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM/dd/yyyy hh:mm a");
            final LocalDateTime localDateTime = LocalDateTime.parse(stringDate, formatter);
            return localDateTime.toInstant(ZoneOffset.UTC);
        } catch (Exception e) {
            throw new DataIgnoredException("Wrong settlement date/time format. ");
        }
    }

    @Override
    public TransactionCluster toTransactionCluster() {
        TransactionType type = findTransactionType();
        Instant date = combineDateAndTime(this.date, time);

        switch (type) {
            case WITHDRAWAL: {
                return createWithdrawal(date);
            }
            default: {
                throw new DataValidationException("Unsupported transaction type. ");
            }
        }
    }

    private TransactionType findTransactionType() {
        return WITHDRAWAL;
    }

    private TransactionCluster createWithdrawal(Instant createdAt) {
        try {
            BigDecimal amount = new BigDecimal(this.amount);
            Currency currency = Currency.fromCode(this.currency);
            BigDecimal feeAmount = new BigDecimal(this.transferFeesPaid);

            boolean ignoredFees = false;
            List<ImportedTransactionBean> related;
            TransactionCluster cluster = null;
            if (ParserUtils.nullOrZero(feeAmount)) {
                related = emptyList();
            } else {
                try {
                    related = List.of(
                        new FeeRebateImportedTransactionBean(
                            null,
                            createdAt,
                            currency,
                            currency,
                            FEE,
                            feeAmount,
                            currency
                        )
                    );
                } catch (Exception e) {
                    ignoredFees = true;
                    related = emptyList();
                }
            }
            cluster = new TransactionCluster(
                ImportedTransactionBean.createDepositWithdrawal(
                    openNodeID,
                    createdAt,
                    currency,
                    currency,
                    WITHDRAWAL,
                    amount.abs(),
                    null
                ),
                related
            );
            if (ignoredFees) {
                if (!nullOrZero(feeAmount) && currency == null) {
                    cluster.setFailedFee(1, "Fee " + (currency != null ? currency.code() : "null")
                        + " currency is not defined");
                }
            }
            return cluster;
        } catch (Exception e) {
            throw new DataValidationException("Something went wrong. ");
        }
    }

}
