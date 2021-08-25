package io.everytrade.server.plugin.impl.everytrade;

import org.knowm.xchange.service.BaseParamsDigest;
import si.mazi.rescu.RestInvocation;

import javax.crypto.Mac;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;

public class EveryTradeApiDigest extends BaseParamsDigest {
    private static final byte[] SPACE = " ".getBytes(StandardCharsets.UTF_8);

    public EveryTradeApiDigest(String base64Key) {
        super(decodeBase64(Objects.requireNonNull(base64Key)), HMAC_SHA_512);
    }

    @Override
    public String digestParams(RestInvocation restInvocation) {
        final Mac sha512Hmac = getMac();

        sha512Hmac.update(restInvocation.getHttpMethod().getBytes(StandardCharsets.UTF_8));
        sha512Hmac.update(SPACE);

        String url = restInvocation.getInvocationUrl();
        url = url.replaceAll("cloud.generalbytes.com/", "cloud.generalbytes.com:7777/");

        sha512Hmac.update(url.getBytes(StandardCharsets.UTF_8));
        if (restInvocation.getRequestBody() != null) {
            sha512Hmac.update(SPACE);
            sha512Hmac.update(restInvocation.getRequestBody().getBytes(StandardCharsets.UTF_8));
        }

        return Base64.getEncoder().encodeToString(sha512Hmac.doFinal());
    }
}
