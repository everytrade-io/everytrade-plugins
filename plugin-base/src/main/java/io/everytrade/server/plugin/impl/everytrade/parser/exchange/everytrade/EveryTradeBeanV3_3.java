package io.everytrade.server.plugin.impl.everytrade.parser.exchange.everytrade;

import com.univocity.parsers.annotations.Format;
import com.univocity.parsers.annotations.Parsed;
import io.everytrade.server.model.TransactionType;
import io.everytrade.server.plugin.api.parser.ImportedTransactionBean;
import io.everytrade.server.plugin.api.parser.TransactionCluster;
import io.everytrade.server.plugin.impl.everytrade.parser.exchange.ExchangeBean;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import static io.everytrade.server.model.Currency.SOL;
import static io.everytrade.server.model.TransactionType.DEPOSIT;
import static io.everytrade.server.model.TransactionType.WITHDRAWAL;
import static lombok.AccessLevel.PRIVATE;

@FieldDefaults(level = PRIVATE)
public class EveryTradeBeanV3_3 extends ExchangeBean {

    Instant date;
    String containerName;
    TransactionType action;
    BigDecimal quantity;
    String note;
    String address;

    ImportedTransactionBean main;
    List<ImportedTransactionBean> related;


    @Parsed(field = "Datum")
    @Format(formats = {"yyyy-MM-dd HH:mm:ss"},
        options = {"locale=US", "timezone=UTC"})
    public void setDate(Date value) {
        date = value.toInstant();
    }

    @Parsed(field = "Kontejner")
    public void setContainerName(String value) {
            containerName = value;
    }

    @Parsed(field = "Typ")
    public void setAction(String value) {
            action = value.equalsIgnoreCase(DEPOSIT.name()) ? DEPOSIT : WITHDRAWAL;
    }

    @Parsed(field = {"Adresa"})
    public void setAddress(String value) {
        address = value;
    }

    @Parsed(field = {"Množství"})
    public void setQuantity(String value) {
        if(action == DEPOSIT) {
            quantity = new BigDecimal(value);
        } else {
           quantity = BigDecimal.ONE;
        }
    }


    @Parsed(field = "Poznámka")
    public void setNote(String value) {
        note = value;
    }

    @Override
    public TransactionCluster toTransactionCluster() {
        if(!note.contains("avid, pravdepodobne staking reward")){
            return null;
        }

        switch (action) {
            case WITHDRAWAL:
                return null;
            case DEPOSIT:
                return createDepositOrWithdrawalTxCluster();
            default:
                throw new IllegalStateException(String.format("Unsupported transaction type %s.", action));
        }
    }

    private TransactionCluster createDepositOrWithdrawalTxCluster() {
        var tx = ImportedTransactionBean.createDepositWithdrawal(
            null,
            date,
            SOL,
            SOL,
            action,
            quantity,
            address,
            note,
            null
        );

        return new TransactionCluster(tx, List.of());
    }

}
