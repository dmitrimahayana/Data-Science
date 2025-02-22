import org.apache.spark.ml.PipelineModel
import org.apache.spark.ml.evaluation.RegressionEvaluator
import org.apache.spark.sql.{SparkSession, functions}
import org.apache.spark.sql.functions.{col, lit}
import org.apache.spark.sql.types.{DoubleType, IntegerType, LongType, StringType}

import java.text.SimpleDateFormat
import java.util.Calendar

object PredictData {
    def main(args: Array[String]): Unit = {
//        val sparkMaster = "spark://172.20.224.1:7077"
//        val sparkMaster = "spark://localhost:7077"
        val sparkMaster = "local[*]"
        val appName = "Scala IDX Stock Prediction"
        val localHostname = java.net.InetAddress.getLocalHost.getHostName
        var mongoDBURL = ""
        if (localHostname.equalsIgnoreCase("dmitri")) {
            mongoDBURL = "mongodb://localhost:27017/";
        } else {
            mongoDBURL = "mongodb://mongodb-server:27017/";
        }
        val mongoDBCollectionInput = mongoDBURL + "kafka.ksql-stock-stream"
        val mongoDBCollectionOutput = mongoDBURL + "kafka.ksql-predict-stock"
        val spark = SparkSession.builder()
          .appName(appName)
          .master(sparkMaster)
          .config("spark.executor.instances", "2")
          .config("spark.executor.memory", "6g")
          .config("spark.executor.cores", "3")
          .config("spark.driver.memory", "6g")
          .config("spark.driver.cores", "3")
          .config("spark.cores.max", "6")
          .config("spark.sql.files.maxPartitionBytes", "12345678")
          .config("spark.sql.files.openCostInBytes", "12345678")
          .config("spark.sql.broadcastTimeout", "1000")
          .config("spark.sql.autoBroadcastJoinThreshold", "100485760")
          .config("spark.sql.shuffle.partitions", "1000")
          .config("spark.mongodb.read.connection.uri", mongoDBCollectionInput) //Docker mongodb
          .config("spark.mongodb.write.connection.uri", mongoDBCollectionOutput) //Docker mongodb
    //      .config("com.mongodb.spark.sql.connector.MongoTableProvider", "D:/03 Data Tools/spark-3.4.1-bin-hadoop3/jars/mongo-spark-connector_2.12-10.2.0.jar")
          .getOrCreate()

        println("Hostname: " + localHostname + " MongoDB URL: " + mongoDBURL)
        println("spark: " + spark.version)
        println("scala: " + util.Properties.versionString)
        println("java: " + System.getProperty("java.version"))

        val df = spark.read.format("mongodb").load()
        df.printSchema()

        val newDf = df.select("id", "ticker", "date", "open", "high", "low", "close", "volume")
          .withColumn("id", col("id").cast(StringType))
          .withColumn("ticker", col("ticker").cast(StringType))
          .withColumn("date", col("date").cast(StringType))
          .withColumn("open", col("open").cast(DoubleType))
          .withColumn("high", col("high").cast(DoubleType))
          .withColumn("low", col("low").cast(DoubleType))
          .withColumn("close", col("close").cast(DoubleType))
          .withColumn("volume", col("volume").cast(LongType))
        newDf.printSchema()
        println("Original Total Row: " + newDf.count())

        val dateFormat = new SimpleDateFormat("yyyy-MM")
        val calendar = Calendar.getInstance()
//        val query = "date like '%"+dateFormat.format(calendar.getTime())+"%'"
        calendar.add(Calendar.MONTH, -1) //Get Last Month Date
        val query = "date like '%" + dateFormat.format(calendar.getTime()) + "%'"
        println("Start Query Dataframe: "+query)
        val filterDf = newDf.filter(query)
        println("Filtered Total Row: " + filterDf.count())

        // And load it back in during production
        println("load model...")
        val currentModelName = "modelGaussian"
        val model2 = PipelineModel.load("/04 Model/Scala-IDX-Stock-Analysis/" + currentModelName)
//        val model2 = PipelineModel.load("D:/00 Project/00 My Project/IdeaProjects/Scala-IDX-Stock-Analysis/modelGaussian")

        // Make predictions.
        println("Testing Data Pipeline...")
        val predictions = model2.transform(filterDf)

        // Formatting column
        var resultDf = predictions.select("prediction", "close", "date", "ticker", "open", "high", "low", "volume")
          .withColumn("id", functions.concat(col("ticker"), lit("_"), col("date").cast(StringType)))
          .withColumn("close", col("prediction").cast(IntegerType))
        resultDf = resultDf.select("id", "ticker", "date", "open", "high", "low", "close", "volume")

        // Write to MongoDB with overwrite/append mode and MUST RUN THIS USING SPARK SUBMIT
        println("Updating MongoDB...")
        resultDf.write.format("mongodb").mode("overwrite").save //MUST RUN THIS USING SPARK SUBMIT

        // Select example rows to display.
        resultDf.show(20)

        // Select (prediction, true label) and compute test error based on label and prediction column
        val evaluator = new RegressionEvaluator()
          .setLabelCol("close")
          .setPredictionCol("prediction")
          .setMetricName("rmse")
        val rmse = evaluator.evaluate(predictions)
        println(s"Root Mean Squared Error (RMSE) on test data = $rmse")

  }
}
