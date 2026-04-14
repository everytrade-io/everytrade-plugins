package io.everytrade.server.plugin.impl.everytrade;

import io.everytrade.server.parser.exchange.EveryTradeApiTransactionBean;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.util.ArrayList;
import java.util.List;

import static lombok.AccessLevel.PRIVATE;

@Data
@NoArgsConstructor
@FieldDefaults(level = PRIVATE)
public class WhaleBooksApiDto {
    String[] header; //TODO: remove header?
    List<EveryTradeApiTransactionBean> transactions = new ArrayList<>();
}
