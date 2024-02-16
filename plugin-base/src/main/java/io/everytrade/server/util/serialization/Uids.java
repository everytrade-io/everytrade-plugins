package io.everytrade.server.util.serialization;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
public class Uids {

    Map<String, Uid> UidS;

    public Uids(Map<String, Uid> uids) {
        this.UidS = uids;
    }

    public void addUid(String uidId, Uid uid) {
        this.UidS.put(uidId,uid);
    }

}

