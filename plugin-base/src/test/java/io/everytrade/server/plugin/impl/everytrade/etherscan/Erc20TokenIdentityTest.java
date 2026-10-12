package io.everytrade.server.plugin.impl.everytrade.etherscan;

import io.everytrade.server.model.Currency;
import org.junit.jupiter.api.Test;

import static io.everytrade.server.model.Currency.BTC;
import static io.everytrade.server.model.Currency.ETH;
import static io.everytrade.server.model.Currency.HEX;
import static io.everytrade.server.model.Currency.USDC;
import static io.everytrade.server.model.Currency.USDT;
import static io.everytrade.server.model.Currency.WETH;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit cover for {@link Erc20TokenIdentity}. No network and no real wallet: an ERC-20 {@code symbol()} is
 * free text, so everything that matters here can be expressed with a literal symbol and a literal
 * contract address.
 * <p>
 * The three impersonating contracts below are the attacker's own, observed on mainnet on 2026-10-12 all
 * declaring the symbol {@code ETH}; the canonical ones were read from the chain the same day by calling
 * {@code symbol()} on them.
 */
class Erc20TokenIdentityTest {

    private static final String FAKE_ETH_1 = "0x4607fd61378d7c5b3186e64834914ba3e353dd16";
    private static final String FAKE_ETH_2 = "0x15037912ab2769f4e6a6ee1522d2cea95eed291a";
    private static final String FAKE_ETH_3 = "0x5399d8d57f54dca4b8e6263eea69aa64689434f2";

    private static final String REAL_WETH = "0xc02aaa39b223fe8d0a0e5c4f27ead9083c756cc2";
    private static final String REAL_USDT = "0xdac17f958d2ee523a2206206994597c13d831ec7";
    private static final String REAL_HEX = "0x2b591e99afe9f32eaa6214f7b7629768c40eeb39";

    /**
     * The rule that removes the whole measured defect. Ether has no contract at all, so a token claiming
     * to be ETH is an impersonator no matter which contract it comes from - there is no address that
     * could make this claim true, including the canonical WETH one.
     */
    @Test
    void aTokenClaimingToBeEtherIsAlwaysRejected() {
        for (String contract : new String[]{FAKE_ETH_1, FAKE_ETH_2, FAKE_ETH_3, REAL_WETH, REAL_USDT, ""}) {
            assertNotNull(Erc20TokenIdentity.rejectionReason(ETH, contract),
                "a token from " + contract + " claiming the symbol ETH must never be booked as ether");
        }
        assertNotNull(Erc20TokenIdentity.rejectionReason(ETH, null),
            "a missing contract address must not be a way around the ether rule");
    }

    @Test
    void aTokenFromItsCanonicalContractIsAccepted() {
        assertNull(Erc20TokenIdentity.rejectionReason(WETH, REAL_WETH));
        assertNull(Erc20TokenIdentity.rejectionReason(USDT, REAL_USDT));
        assertNull(Erc20TokenIdentity.rejectionReason(HEX, REAL_HEX));
    }

    @Test
    void aTokenClaimingAKnownSymbolFromAForeignContractIsRejected() {
        final String reason = Erc20TokenIdentity.rejectionReason(USDT, FAKE_ETH_1);
        assertNotNull(reason, "a contract that is not Tether must not be booked as USDT");
        assertTrue(reason.contains(REAL_USDT),
            "the reason must name the contract we expected, so the rejection can be checked: " + reason);

        assertNotNull(Erc20TokenIdentity.rejectionReason(WETH, REAL_USDT),
            "swapping two canonical contracts must not pass either");
    }

    /**
     * EIP-55 encodes a checksum in the letter case and Etherscan returns the checksummed form in some
     * fields and the lower-case form in others, so comparison has to normalise. A test that only ever
     * passed lower-case would miss the day the API changes which form it sends.
     */
    @Test
    void contractComparisonIgnoresChecksumCaseAndSurroundingSpace() {
        assertNull(Erc20TokenIdentity.rejectionReason(WETH, "0xC02aaA39b223FE8D0A0e5C4F27eAD9083C756Cc2"));
        assertNull(Erc20TokenIdentity.rejectionReason(WETH, "  " + REAL_WETH + "  "));
        assertNull(Erc20TokenIdentity.rejectionReason(WETH, REAL_WETH.toUpperCase().replace("0X", "0x")));
    }

    /**
     * Deliberate scope limit, pinned so that narrowing it later is a decision somebody makes on purpose
     * rather than a side effect: a symbol we hold no contract for keeps importing. Dropping every unknown
     * token would stop legitimate holdings that work today.
     */
    @Test
    void aSymbolWithNoCanonicalContractIsStillTrusted() {
        assertFalse(Erc20TokenIdentity.hasCanonicalContract(BTC));
        assertNull(Erc20TokenIdentity.rejectionReason(BTC, FAKE_ETH_1),
            "no canonical contract is known for this symbol, so the row keeps its previous behaviour");
    }

    @Test
    void theRegistryAnswersWhichSymbolsItPins() {
        assertTrue(Erc20TokenIdentity.hasCanonicalContract(WETH));
        assertTrue(Erc20TokenIdentity.hasCanonicalContract(USDT));
        assertTrue(Erc20TokenIdentity.hasCanonicalContract(USDC));
        assertFalse(Erc20TokenIdentity.hasCanonicalContract(Currency.CZK));
    }
}
