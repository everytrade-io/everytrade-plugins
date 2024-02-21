package io.everytrade.server.util.serialization;

import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import static io.everytrade.server.util.serialization.SequenceIdentifierType.START;
import static io.everytrade.server.util.serialization.SequenceIdentifierType.STATUS;

@Getter
@Setter
public class Uid {

    Map<SequenceIdentifierType, String> uid;

    public Uid(Map<SequenceIdentifierType, String> uid) {
        this.uid = new HashMap<>(uid);
    }

    public void setUid(SequenceIdentifierType key, String value) {
        if (uid != null && uid.get(key) != null) {
            uid.put(key, value);
        } else {
            if(uid == null) {
                uid = new TreeMap<>();
                uid.put(key,value);
            } else if(uid.containsKey(key)) {
                uid.replace(key, value);
            } else {
                this.
                uid.put(key,value);
            }
        }
    }
}

