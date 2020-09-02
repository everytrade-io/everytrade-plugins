package io.everytrade.server.plugin.api.connector;

public interface IConnector extends AutoCloseable {
    /**
     * Returns the connector's identifier. Should be unique among all the parent plugin's connectors.
     * @return connector ID
     */
    String getId();

    /**
     * Download transactions.
     * @param lastTransactionId id of last previously downloaded transaction. Represents the state of synchronization
     *                         between the source of data and Everytrade system, a download resume point.
     * @return download result
     */
    DownloadResult getTransactions(String lastTransactionId);

    /**
     * {@inheritDoc}
     */
    default void close() {};
}
