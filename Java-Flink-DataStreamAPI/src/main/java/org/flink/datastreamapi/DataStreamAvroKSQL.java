package org.flink.datastreamapi;

import org.apache.flink.api.java.ExecutionEnvironment;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.CheckpointingMode;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.CheckpointConfig;
import org.apache.flink.streaming.api.environment.ExecutionCheckpointingOptions;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.table.api.EnvironmentSettings;
import org.apache.flink.table.api.Table;
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment;
import org.apache.flink.types.Row;
import org.apache.flink.util.CloseableIterator;

public class DataStreamAvroKSQL {
    public static void main(String[] args) throws Exception {
        String topic1 = "streaming.goapi.idx.stock.json";
        String topic2 = "streaming.goapi.idx.companies.json";
        String topic3 = "KSQLGROUPSTOCK"; //KSQLDB Table
        String topic4 = "KSQLGROUPCOMPANY"; //KSQLDB Table
        String group = "my-flink-group-ksql";
        String jarsPath = "D:/00 Project/00 My Project/Jars/Java-Flink-DataStream/";
        StreamExecutionEnvironment env;
        String bootStrapServer;
        String schemaHost;

        Boolean runLocal = Boolean.TRUE;
        if (runLocal) {
            bootStrapServer = "localhost:39092,localhost:39093,localhost:39094";
            schemaHost = "http://localhost:8282";
            env = StreamExecutionEnvironment.getExecutionEnvironment();
        } else {
            bootStrapServer = "kafka1:19092,kafka2:19093,kafka3:19094"; //Docker Kafka URL
            schemaHost = "http://schema-registry:8282"; //Docker Schema Registry URL
            env = StreamExecutionEnvironment.createRemoteEnvironment(
                    "localhost",
                    8383,
                    jarsPath + "flink-connector-kafka-1.17.1.jar",
                    jarsPath + "kafka-clients-3.2.3.jar",
                    jarsPath + "flink-avro-1.17.1.jar",
                    jarsPath + "avro-1.11.0.jar",
                    jarsPath + "flink-avro-confluent-registry-1.17.1.jar",
                    jarsPath + "kafka-schema-registry-client-7.4.0.jar",
                    jarsPath + "jackson-core-2.12.5.jar",
                    jarsPath + "jackson-databind-2.14.2.jar",
                    jarsPath + "jackson-annotations-2.14.2.jar",
                    jarsPath + "guava-30.1.1-jre.jar"
            );

            // start a checkpoint every 1000 ms
            env.enableCheckpointing(1000);
            // advanced options:
            // set mode to exactly-once (this is the default)
            env.getCheckpointConfig().setCheckpointingMode(CheckpointingMode.EXACTLY_ONCE);
            // make sure 500 ms of progress happen between checkpoints
            env.getCheckpointConfig().setMinPauseBetweenCheckpoints(500);
            // checkpoints have to complete within one minute, or are discarded
            env.getCheckpointConfig().setCheckpointTimeout(60000);
            // only two consecutive checkpoint failures are tolerated
            env.getCheckpointConfig().setTolerableCheckpointFailureNumber(2);
            // allow only one checkpoint to be in progress at the same time
            env.getCheckpointConfig().setMaxConcurrentCheckpoints(1);
            // enable externalized checkpoints which are retained
            // after job cancellation
            env.getCheckpointConfig().setExternalizedCheckpointCleanup(CheckpointConfig.ExternalizedCheckpointCleanup.RETAIN_ON_CANCELLATION);
            // enables the unaligned checkpoints
            env.getCheckpointConfig().enableUnalignedCheckpoints();
            // sets the checkpoint storage where checkpoint snapshots will be written
            env.getCheckpointConfig().setCheckpointStorage("file:///opt/flink/checkpoints");
            // enable checkpointing with finished tasks
            Configuration config = new Configuration();
            config.set(ExecutionCheckpointingOptions.ENABLE_CHECKPOINTS_AFTER_TASKS_FINISH, true);
            env.configure(config);
        }

        EnvironmentSettings settings = EnvironmentSettings
                .newInstance()
                .inStreamingMode()
                .build();

        StreamTableEnvironment tableEnv = StreamTableEnvironment.create(env, settings);

        //SQL TABLE MUST USE UPPERCASE COLUMN NAME
        tableEnv.executeSql("CREATE TABLE flink_ksql_groupstock (" +
                "  `EVENT_TIME` TIMESTAMP(3) METADATA FROM 'timestamp', " +
                "  `STOCKID` STRING, " +
                "  `TICKER` STRING, " +
                "  `DATE` STRING, " +
                "  `OPEN` DOUBLE, " +
                "  `HIGH` DOUBLE, " +
                "  `LOW` DOUBLE, " +
                "  `CLOSE` DOUBLE, " +
                "  `VOLUME` BIGINT " +
                ") WITH (" +
                "  'connector' = 'kafka', " +
                "  'topic' = '" + topic3 + "', " +
                "  'properties.bootstrap.servers' = '" + bootStrapServer + "', " +
                "  'properties.group.id' = '" + group + "', " +
                "  'scan.startup.mode' = 'earliest-offset', " +
                "  'value.format' = 'avro-confluent', " +
                "  'value.avro-confluent.url' = '" + schemaHost + "' " +
                ")");

        //SQL TABLE MUST USE UPPERCASE COLUMN NAME
        tableEnv.executeSql("CREATE TABLE flink_ksql_groupcompany (" +
                "  `EVENT_TIME` TIMESTAMP(3) METADATA FROM 'timestamp', " +
                "  `COMPANYID` STRING, " +
                "  `TICKER` STRING, " +
                "  `NAME` STRING, " +
                "  `LOGO` STRING " +
                ") WITH (" +
                "  'connector' = 'kafka', " +
                "  'topic' = '" + topic4 + "', " +
                "  'properties.bootstrap.servers' = '" + bootStrapServer + "', " +
                "  'properties.group.id' = '" + group + "', " +
                "  'scan.startup.mode' = 'earliest-offset', " +
                "  'value.format' = 'avro-confluent', " +
                "  'value.avro-confluent.url' = '" + schemaHost + "' " +
                ")");

        tableEnv.executeSql("CREATE TABLE flink_mongodb_stock (" +
                "  `id` STRING, " +
                "  `ticker` STRING, " +
                "  `date` STRING, " +
                "  `open` DOUBLE, " +
                "  `high` DOUBLE, " +
                "  `low` DOUBLE, " +
                "  `close` DOUBLE " +
                ") WITH (" +
                "   'connector' = 'mongodb'," +
                "   'uri' = 'mongodb://localhost:27017'," +
                "   'database' = 'kafka'," +
                "   'collection' = 'ksql-stock-stream'" +
                ");");

        tableEnv.executeSql("CREATE TABLE flink_kafka_topic_stock (" +
                "  `id` STRING, " +
                "  `ticker` STRING, " +
                "  `name` STRING, " +
                "  `logo` STRING " +
                ") WITH (" +
                "  'connector' = 'kafka', " +
                "  'topic' = '" + topic2 + "', " +
                "  'properties.bootstrap.servers' = '" + bootStrapServer + "', " +
                "  'properties.group.id' = '" + group + "', " +
                "  'scan.startup.mode' = 'earliest-offset', " +
                "  'value.format' = 'avro-confluent', " +
                "  'value.avro-confluent.url' = '" + schemaHost + "' " +
                ")");

        tableEnv.executeSql("CREATE TABLE flink_kafka_topic_company (" +
                "  `id` STRING, " +
                "  `ticker` STRING, " +
                "  `date` STRING, " +
                "  `open` DOUBLE, " +
                "  `high` DOUBLE, " +
                "  `low` DOUBLE, " +
                "  `close` DOUBLE, " +
                "  `volume` BIGINT " +
                ") WITH (" +
                "  'connector' = 'kafka', " +
                "  'topic' = '" + topic1 + "', " +
                "  'properties.bootstrap.servers' = '" + bootStrapServer + "', " +
                "  'properties.group.id' = '" + group + "', " +
                "  'scan.startup.mode' = 'earliest-offset', " +
                "  'value.format' = 'avro-confluent', " +
                "  'value.avro-confluent.url' = '" + schemaHost + "' " +
                ")");

        // Define a query using the Kafka source
        Table resultTable1 = tableEnv.sqlQuery("SELECT " +
                "  table1.`EVENT_TIME`," +
                "  `STOCKID`," +
                "  table1.`TICKER`," +
                "  `DATE`," +
                "  `OPEN`," +
                "  `HIGH`," +
                "  `LOW`," +
                "  `CLOSE`," +
                "  `VOLUME`, " +
                "  `NAME`, " +
                "  `LOGO` " +
                "  FROM flink_ksql_groupstock table1" +
                "  INNER JOIN flink_ksql_groupcompany table2" +
                "  ON table1.TICKER = table2.TICKER");
//        Table resultTable2 = tableEnv.sqlQuery("SELECT * FROM flink_ksql_groupstock");
//        Table resultTable3 = tableEnv.sqlQuery("SELECT * FROM flink_ksql_groupcompany");
//        Table resultTable4 = tableEnv.sqlQuery("SELECT * FROM flink_mongodb_stock LIMIT 5");
//        Table resultTable5 = tableEnv.sqlQuery("SELECT * FROM flink_kafka_topic_stock LIMIT 5");
//        Table resultTable6 = tableEnv.sqlQuery("SELECT * FROM flink_kafka_topic_company LIMIT 5");

        for (CloseableIterator<Row> it = resultTable1.execute().collect(); it.hasNext(); ) {
            Row row = it.next();
            System.out.println("Value: " + row.getField("EVENT_TIME") + " --- " + row.getField("STOCKID") + " --- " + row.getField("CLOSE") + " --- " + row.getField("NAME"));
        }
//        DataStream<Row> resultStream1 = tableEnv.toAppendStream(resultTable1, Row.class);
//        DataStream<Row> resultStream2 = tableEnv.toAppendStream(resultTable2, Row.class);
//        DataStream<Row> resultStream3 = tableEnv.toAppendStream(resultTable3, Row.class);
//        DataStream<Row> resultStream4 = tableEnv.toAppendStream(resultTable4, Row.class);
//        DataStream<Row> resultStream5 = tableEnv.toAppendStream(resultTable5, Row.class);
//        DataStream<Row> resultStream6 = tableEnv.toAppendStream(resultTable5, Row.class);

        // Print the results to stdout for testing
//        resultStream1.printToErr();
//        resultStream2.print();
//        resultStream3.print();
//        resultStream4.print();
//        resultStream5.print();
//        resultStream6.print();

        // Execute the Flink job
        env.execute("Flink Kafka SQL Consumer");
    }
}
