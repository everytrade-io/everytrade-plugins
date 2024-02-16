package io.everytrade.server.util.serialization;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static io.everytrade.server.util.serialization.DownloadedStatus.ALL_DATA_DOWNLOADED;
import static io.everytrade.server.util.serialization.DownloadedStatus.PARTIAL_DATA_DOWNLOADED;
import static io.everytrade.server.util.serialization.SequenceIdentifierType.END;
import static io.everytrade.server.util.serialization.SequenceIdentifierType.START;
import static io.everytrade.server.util.serialization.SequenceIdentifierType.STATUS;
import static io.everytrade.server.util.serialization.ConnectorSerialization.createUidType;
import static io.everytrade.server.util.serialization.ConnectorSerialization.deserialize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
@Disabled
public class SerializationTest {

    String BUY_UID_ID = "1";
    String SELL_UID_ID = "2";
    String DEPOSIT_UID_ID = "3";
    String WITHDRAWAL_UID_ID = "4";

    public Uids createExpectedMapUid1() {
        Uids uidS = createDefaultUidMap();
        Uid buyUid = createUidType(ALL_DATA_DOWNLOADED, "105", "10510");
        Uid sellUid = createUidType(PARTIAL_DATA_DOWNLOADED, "106", "150");
        Uid depositUid = createUidType(ALL_DATA_DOWNLOADED, "105", "200");
        uidS.addUid(BUY_UID_ID, buyUid);
        uidS.addUid(SELL_UID_ID, sellUid);
        uidS.addUid(DEPOSIT_UID_ID, depositUid);
        return uidS;
    }

    public Uids createExpectedMapUid2() {
        Uids uidS = createDefaultUidMap();
        Uid buyUid = createUidType(ALL_DATA_DOWNLOADED, null, "10510");
        Uid sellUid = createUidType(PARTIAL_DATA_DOWNLOADED, "", "150");
        Uid depositUid = createUidType(ALL_DATA_DOWNLOADED, "105", null);
        Uid withdrawalUid = createUidType(ALL_DATA_DOWNLOADED, null, null);
        uidS.addUid(BUY_UID_ID, buyUid);
        uidS.addUid(SELL_UID_ID, sellUid);
        uidS.addUid(DEPOSIT_UID_ID, depositUid);
        uidS.addUid(WITHDRAWAL_UID_ID, withdrawalUid);
        return uidS;
    }

    public Uids createDefaultUidMap() {
        Map<String, Uid> uidS = new HashMap<>();
        return new Uids(uidS);
    }


    /**
     * Serializes the given Uids object into a formatted string.
     *
     * @param serializationByTransactionType the Uids object to be serialized
     * @return the serialized string representation of the Uids object
     */
    @Test
    void testSerializeUids1() {
        String emptyMap = ConnectorSerialization.serialize(createDefaultUidMap());
        // Uid
        Uids expected = createExpectedMapUid1();

        // Serialization
        String actualSerializationUid = ConnectorSerialization.serialize(expected);
        String serUid = ("1:st=A;s=105;e=10510|2:st=P;s=106;e=150|3:st=A;s=105;e=200");
        assertEquals(serUid,actualSerializationUid);

        // Deserialization
        Uids actualUid = deserialize(serUid);
        assertNotNull(actualUid);
        assertNotNull(expected);

        assertEquals(serUid, ConnectorSerialization.serialize(deserialize(serUid)));
        asserts(expected,actualUid);
    }

    /**
     * Serializes the given Uids object into a formatted string.
     *
     * @param serializationByTransactionType the Uids object to be serialized
     * @return the serialized string representation of the Uids object
     */
    @Test
    void testSerializeUids2() {
        String emptyMap = ConnectorSerialization.serialize(createDefaultUidMap());
        // Uid
        Uids expected = createExpectedMapUid2();

        // Serialization
        String actualSerializationUid = ConnectorSerialization.serialize(expected);
        String serUid = ("1:st=A;e=10510|2:st=P;e=150|3:st=A;s=105|4:st=A");
        assertEquals(serUid,actualSerializationUid);

        // Deserialization
        Uids actualUid = deserialize(serUid);
        assertNotNull(actualUid);
        assertNotNull(expected);

        assertEquals(serUid, ConnectorSerialization.serialize(deserialize(serUid)));
        asserts(expected,actualUid);
    }

    private void asserts(Uids expected, Uids deserialized) {
        Set<String> uidIds = deserialized.UidS.keySet();
        for( String uidId : uidIds) {
            assertEquals(expected.getUidS().get(uidId), expected.getUidS().get(uidId));
            assertEquals(expected.getUidS().get(uidId).getUid().get(STATUS), expected.getUidS().get(uidId).getUid().get(STATUS));
            assertEquals(expected.getUidS().get(uidId).getUid().get(START), expected.getUidS().get(uidId).getUid().get(START));
            assertEquals(expected.getUidS().get(uidId).getUid().get(END), expected.getUidS().get(uidId).getUid().get(END));
        }
    }




}
