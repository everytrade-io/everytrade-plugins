package io.everytrade.server.plugin.impl.everytrade.etherscan;

import io.everytrade.server.model.Currency;

import java.util.Map;

import static io.everytrade.server.model.Currency.DAI;
import static io.everytrade.server.model.Currency.ETH;
import static io.everytrade.server.model.Currency.HEX;
import static io.everytrade.server.model.Currency.LINK;
import static io.everytrade.server.model.Currency.PEPE;
import static io.everytrade.server.model.Currency.SHIB;
import static io.everytrade.server.model.Currency.UNI;
import static io.everytrade.server.model.Currency.USDC;
import static io.everytrade.server.model.Currency.USDT;
import static io.everytrade.server.model.Currency.WBTC;
import static io.everytrade.server.model.Currency.WETH;

/**
 * Decides whether an ERC-20 transfer may be booked as the currency its {@code tokenSymbol} claims to be.
 * <p>
 * An ERC-20 symbol is attacker-controlled free text. Anyone can deploy a contract whose {@code symbol()}
 * returns {@code "ETH"} or {@code "USDT"} and emit Transfer events naming any address they like - the
 * named address does not have to consent, hold the token, or ever interact with the contract. Mapping
 * that symbol straight onto a {@link Currency} therefore books a stranger's worthless token as a real
 * asset in the customer's ledger.
 * <p>
 * MEASURED on 2026-10-12 on a real wallet: three unrelated contracts ({@code 0x4607fd6137…},
 * {@code 0x15037912ab…}, {@code 0x5399d8d57f…}) all declared the symbol {@code ETH} and produced four
 * phantom WITHDRAWAL rows totalling 36.178196180868758 ETH, against a genuine net position of
 * 0.002902211565621002 ETH. Two of the fakes copy the amount and the day of a genuine withdrawal, so a
 * human comparing our list against a block explorer cannot separate them either, which is the point of
 * the attack.
 * <p>
 * How much that costs depends on one connector setting, and the honest answer is not "always a lot".
 * With {@code importWithdrawalsAsSells} off - the shipped default - the rows are WITHDRAWALs, and the
 * lot engine ignores WITHDRAWAL entirely while the trading statement filters it out, so they move no
 * computed number; they sit in the ledger, count against the plan limit, and nothing on screen explains
 * them. With that setting ON the same rows become market-priced SELLs: 36.18 ETH of disposals that never
 * happened, stocked as a negative lot. Either way the wallet's own history is wrong, which is reason
 * enough not to import them.
 * <p>
 * The wallet is deliberately not named here. This repository is public, and naming it would publish a
 * link between a person's on-chain history and a Whalebooks user that its owner never agreed to
 * publish. The three contract addresses ARE named on purpose - they belong to the attacker, and
 * without them the mechanism could not be re-verified by anyone reading this.
 * <p>
 * Two rules, in descending order of certainty:
 * <ol>
 *   <li><b>The native currency can never arrive as a token.</b> Ether moves as the value of a
 *       transaction, never as an ERC-20 Transfer event, so a token claiming {@code ETH} is an
 *       impersonator with no exception. This is a property of the protocol, not a judgement about any
 *       particular contract, and it is what removes the whole measured defect.</li>
 *   <li><b>Where the canonical contract is known, it has to match.</b> {@link #CANONICAL_CONTRACTS}
 *       pins the Ethereum mainnet contract for the tokens worth impersonating. A claim to one of those
 *       symbols from any other contract is rejected; a transfer from the canonical contract is
 *       accepted exactly as before.</li>
 * </ol>
 * A symbol with no entry here keeps the previous behaviour and is still trusted. That is deliberate:
 * dropping every unlisted token would stop importing legitimate holdings that work today, and deciding
 * to narrow the product that way is not this defect's call. The residual exposure is impersonation of
 * a symbol we hold no contract for, which wins the attacker a low-value asset rather than a high-value
 * one; widening the map is the way to close it, one verified address at a time.
 * <p>
 * Every address below was read from the chain on 2026-10-12 by calling {@code symbol()} (selector
 * {@code 0x95d89b41}) and {@code decimals()} ({@code 0x313ce567}) on it through the Etherscan proxy
 * module, and all ten answered the symbol they are mapped to. Addresses are stored lower-case: EIP-55
 * mixes case as a checksum and Etherscan returns the checksummed form in some fields and the
 * lower-case form in others, so comparison has to normalise rather than trust either.
 */
public final class Erc20TokenIdentity {

    private static final Map<Currency, String> CANONICAL_CONTRACTS = Map.ofEntries(
        Map.entry(WETH, "0xc02aaa39b223fe8d0a0e5c4f27ead9083c756cc2"),
        Map.entry(USDT, "0xdac17f958d2ee523a2206206994597c13d831ec7"),
        Map.entry(USDC, "0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48"),
        Map.entry(DAI, "0x6b175474e89094c44da98b954eedeac495271d0f"),
        Map.entry(WBTC, "0x2260fac5e5542a773aa44fbcfedf7c193bc2c599"),
        Map.entry(LINK, "0x514910771af9ca656af840dff83e8264ecf986ca"),
        Map.entry(UNI, "0x1f9840a85d5af5bf1d1762f925bdaddc4201f984"),
        Map.entry(SHIB, "0x95ad61b0a150d79219dcf64e1e6cc01f0b64c4ce"),
        Map.entry(PEPE, "0x6982508145454ce325ddbe47a25d4ec3d2311933"),
        Map.entry(HEX, "0x2b591e99afe9f32eaa6214f7b7629768c40eeb39")
    );

    private Erc20TokenIdentity() {
    }

    /**
     * @param claimed         the currency the token's {@code symbol()} claims to be
     * @param contractAddress the contract that emitted the Transfer event
     * @return why the row has to be ignored, or {@code null} when the claim is acceptable
     */
    public static String rejectionReason(Currency claimed, String contractAddress) {
        if (claimed == ETH) {
            return "ether is never transferred as an ERC-20 token, so this contract only claims to be ETH";
        }
        final String canonical = CANONICAL_CONTRACTS.get(claimed);
        if (canonical == null) {
            return null;
        }
        final String actual = normalize(contractAddress);
        if (canonical.equals(actual)) {
            return null;
        }
        return String.format("%s lives at contract %s, not at %s", claimed, canonical, actual.isEmpty() ? "(none)" : actual);
    }

    public static boolean hasCanonicalContract(Currency currency) {
        return CANONICAL_CONTRACTS.containsKey(currency);
    }

    private static String normalize(String contractAddress) {
        return contractAddress == null ? "" : contractAddress.trim().toLowerCase();
    }
}
