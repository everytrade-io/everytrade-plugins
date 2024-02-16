package io.everytrade.server.util.serialization;

import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

import static io.everytrade.server.util.serialization.SequenceIdentifierType.STATUS;
import static io.everytrade.server.util.serialization.SequenceIdentifierType.END;
import static io.everytrade.server.util.serialization.SequenceIdentifierType.START;

public class ConnectorSerialization {

    public static final String DIVIDER_ONE = "|";
    public static final String EQUALS = "=";
    public static final String DIVIDER_TWO = ":";
    public static final String DIVIDER_THREE = ";";
    public static final String EMPTY_SERIALIZER = "";

    String BUY_UID_ID = "1";
    String SELL_UID_ID = "2";
    String DEPOSIT_UID_ID = "3";

    Uids uids;

    public ConnectorSerialization(Uids uids) {
        if(uids == null) {
            this.uids = createDefaultUidMap();
        } else {
            this.uids = uids;
        }
    }

    /**
     * Serializes the given Uids object into a formatted string.
     *
     * @param serializationByTransactionType the Uids object to be serialized
     * @return the serialized string representation of the Uids object
     */
    public static String serialize(Uids serializationByTransactionType) {
        StringBuilder builder = new StringBuilder();
        Iterator<Map.Entry<String, Uid>> iterator = serializationByTransactionType.getUidS().entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Uid> entry = iterator.next();
            builder.append(entry.getKey()).append(DIVIDER_TWO);
            Iterator<Map.Entry<SequenceIdentifierType, String>> iter = entry.getValue().getUid().entrySet().iterator();
            while (iter.hasNext()) {
                Map.Entry<SequenceIdentifierType, String> ent = iter.next();
                builder.append(ent.getKey().getCode()).append(EQUALS).append(ent.getValue());
                if(iter.hasNext()) {
                    builder.append(DIVIDER_THREE);
                }
            }
            if(iterator.hasNext()) {
                builder.append(DIVIDER_ONE);
            }
        }
        String string1 = builder.toString();
        String string = string1.replace("null","");
        return string;
    }

    public static Uids deserialize(String serializedString) {
        Map<String, Uid> uids = new TreeMap<>();
        if(serializedString != null && !serializedString.equals(EMPTY_SERIALIZER)) {
            String[] parts = serializedString.split("\\" + DIVIDER_ONE);
            for (String part : parts) {
                String[] uidList = part.split(DIVIDER_TWO);
                String key = uidList[0];
                Map<SequenceIdentifierType, String> uid = new TreeMap<>();
                String[] values = uidList[1].split(DIVIDER_THREE);

                for (String entry : values) {
                    String[] keyValue = entry.split(EQUALS);
                    String innerKey = keyValue[0];
                    try {
                        String value = keyValue[1];
                        uid.put(SequenceIdentifierType.fromCode(innerKey), value);
                    } catch (ArrayIndexOutOfBoundsException ignore) {

                    }
                }
                uids.put(key, new Uid(uid));
            }
        }
        return new Uids(uids);
    }

    public static Uids createDefaultUidMap() {
        Map<String, Uid> uidS = new TreeMap<>();
        return new Uids(uidS);
    }

    public String getStartSequenceIdentifierById(String uidSequence) {
        Uid uid = uids.getUidS().get(uidSequence);
        return uid.getUid().get(START);
    }

    public String getEndSequenceIdentifierById(int uidSequence) {
        return uids.getUidS().get(uidSequence).getUid().get(END);
    }

    public void setStartSequenceIdentifierById(int uidSequence, String startId) {
        Map<SequenceIdentifierType, String> uid = uids.getUidS().get(uidSequence).getUid();
        uid.put(START,startId);
    }

    public void setEndSequenceIdentifierById(int uidSequence, String endId) {
        Map<SequenceIdentifierType, String> uid = uids.getUidS().get(uidSequence).getUid();
        uid.put(END,endId);
    }

    public static Uid createUidType(DownloadedStatus status, String start, String end) {
        Map<SequenceIdentifierType, String> map = new TreeMap<>();
        if (status != null) {
            map.put(STATUS, status.getCode());
        }
        if (start != null && !EMPTY_SERIALIZER.equals(start)) {
            map.put(START, start);
        }
        if (end != null && !EMPTY_SERIALIZER.equals(end)) {
            map.put(END, end);
        }
        return new Uid(map);
    }

}