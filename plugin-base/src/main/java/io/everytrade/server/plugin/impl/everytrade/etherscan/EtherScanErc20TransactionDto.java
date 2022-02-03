package io.everytrade.server.plugin.impl.everytrade.etherscan;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

import static lombok.AccessLevel.PRIVATE;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = PRIVATE)
@EqualsAndHashCode(callSuper = true)
public class EtherScanErc20TransactionDto extends EtherScanTransactionDto {
    String tokenName;
    String tokenSymbol;
    int tokenDecimal;
}
