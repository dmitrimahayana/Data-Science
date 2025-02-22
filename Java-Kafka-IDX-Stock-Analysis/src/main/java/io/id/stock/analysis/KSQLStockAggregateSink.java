package io.id.stock.analysis;

import io.confluent.ksql.api.client.Client;
import io.confluent.ksql.api.client.Row;
import io.confluent.ksql.api.client.StreamedQueryResult;
import io.id.stock.analysis.Module.KSQLDBConnection;
import io.id.stock.analysis.Module.MongoDBStock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutionException;

public class KSQLStockAggregateSink {
    private static final Logger log = LoggerFactory.getLogger(KStreamStockAggregate.class.getSimpleName());

    public static String createJsonString(Row row){
        String jsonCol = row.columnNames().toString().replace("[", "").replace("]", "").replace(" ", "").toLowerCase();
        String jsonVal = row.values().toString().replace("[", "").replace("]", "").replace(", ", " ");
        String[] jsonColSplit = jsonCol.split(",");
        String[] jsonValSplit = jsonVal.split(",");
        String finalJson= "";
        if (jsonColSplit.length == jsonValSplit.length){
            for(int i = 0; i < jsonColSplit.length; i++){
                if(i == 0){
                    finalJson = "{" + jsonColSplit[i] + ":" + jsonValSplit[i];
                } else if (i == jsonColSplit.length-1) {
                    finalJson = finalJson + "," + jsonColSplit[i] + ":" + jsonValSplit[i] + "}";
                } else {
                    finalJson = finalJson + "," + jsonColSplit[i] + ":" + jsonValSplit[i];
                }
            }
        } else {
            log.error("Column: "+row.columnNames()+" - value: "+row.values());
            log.error("Total Column " + jsonColSplit.length + "is not match with Total Value " + jsonValSplit.length);
        }
        return finalJson;
    }

    public static void main(String[] args) {
        String ksqlHost = "localhost";
        int ksqlPort = 9088;
        KSQLDBConnection conn = new KSQLDBConnection(ksqlHost, ksqlPort);
        Client ksqlClient = conn.createConnection();

        //Create MongoDB Connection
//        MongoDBStock mongoDBConn = new MongoDBStock("mongodb://localhost:27017");
        MongoDBStock mongoDBConn = new MongoDBStock("mongodb://localhost:27017"); //Docker mongodb
        mongoDBConn.createConnection();

//        Map<String, Object> properties = Collections.singletonMap(
//                "auto.offset.reset", "earliest"
//        );

        // Add shutdown hook to stop the Kafka client threads.
        // You can optionally provide a timeout to `close`.
        Runtime.getRuntime().addShutdownHook(new Thread(ksqlClient::close));

        // Add shutdown hook to stop the Kafka client threads.
        // You can optionally provide a timeout to `close`.
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            @Override
            public void run() {
                log.info("close ksql client...");
                ksqlClient.close();
            }
        }));

        String pushQueryStock = "SELECT * FROM KSQLGROUPSTOCK EMIT CHANGES;";

        //Using Sync query
        try {
            StreamedQueryResult streamedQueryStock = ksqlClient.streamQuery(pushQueryStock).get();

            while(true) {
                // Block until a new row is available
                Row rowStock = streamedQueryStock.poll();
                if (rowStock != null) {
                    String finalJson = createJsonString(rowStock);
                    mongoDBConn.insertOrUpdate("kafka", "ksql-stock-stream", finalJson);
                    log.info("Sync Query Stock Result "+ finalJson);
                }
            }
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

//        //Using Async query
//        ksqlClient.streamQuery(pushQueryStock, properties)
//                .thenAccept(localStreamQuery -> {
//                    log.info("Push query has started. Query ID: " + localStreamQuery.queryID());
//                    //Use RowSubscr
//                    RowSubscriber subscriber = new RowSubscriber();
//                    localStreamQuery.subscribe(subscriber);
//                }).exceptionally(e -> {
//                    log.info("Request failed: " + e);
//                    return null;
//                });
    }
}
