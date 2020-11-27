package io.everytrade.server.plugin.impl.everytrade.parser;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

public class SignatureValidator {
    private List<String> tokens;

    // validate provided header with the signature
    // returns: valid, not valid
    public boolean validate(List<String> header, String signature) {
        tokens = Arrays.asList(signature.split(":\\|")[1].split("\\|"));
        Iterator<String> s = this.tokens.iterator();
        Iterator<String> h = header.iterator();

        // signature should be empty, return fail in such case
        if (!s.hasNext()) {
            return false;
        }
        String sToken = s.next();

        // empty header returns fail too
        while (h.hasNext() && sToken != null) {
            String hToken = h.next();
            if (sToken.startsWith("^") && sToken.endsWith("$")) {
                String regex = sToken.substring(1, sToken.length() - 1);
                if (hToken != null && hToken.matches(regex)) {
                    if (s.hasNext()) {
                        sToken = s.next();
                    } else {
                        return true;    // all signatures done, win
                    }
                }
            } else if (sToken.equals(hToken)) {
                if (s.hasNext()) {
                    sToken = s.next();
                } else {
                    return true;    // all signatures done, win
                }
            }
        }
        return false;
    }

}

