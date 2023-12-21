package org.recommender

import java.io.File
import scala.io.Source
import org.apache.spark.{SparkConf, SparkContext}
import org.apache.spark.SparkContext._
import org.apache.spark.rdd._
import org.apache.spark.mllib.recommendation.{ALS, Rating, MatrixFactorizationModel}

object Recommender {
  def main(args: Array[String]): Unit = {
    val config = new SparkConf().setMaster("spark://spark-master:7077").setAppName("MovieRecommenderApplication")
    val spark = new SparkContext(config)
    spark.setLogLevel("WARN")
    
    try {
      // Check if there are any command-line arguments
      if (args.length > 0) {
        // Iterate through the arguments
        for (className <- args) {
          // Load personal ratings
          val personalRatings = spark.textFile("hdfs://namenode:8020/input/$className/personalRatings.txt").map { line =>
            val fields = line.split("::")
            Rating(fields(0).toInt, fields(1).toInt, fields(2).toDouble)
          }.filter(_.rating > 0.0)

          val myRatings = personalRatings.collect()
          val myRatingsRDD = spark.parallelize(myRatings, 1)

          // Load ratings and movie titles
          val ratings = spark.textFile("hdfs://namenode:8020/input/$className/ratings.txt").map { line =>
            val fields = line.split("::")
            // Format: (timestamp % 10, Rating(userId, movieId, rating))
            (fields(3).toLong % 10, Rating(fields(0).toInt, fields(1).toInt, fields(2).toDouble))
          }

          val movies = spark.textFile("hdfs://namenode:8020/input/$className/products.txt").map { line =>
            val fields = line.split("::")
            // Format: (movieId, movieName)
            (fields(0).toInt, fields(1))
          }.collect().toMap

          val numRatings = ratings.count()
          val numUsers = ratings.map(_._2.user).distinct().count()
          val numMovies = ratings.map(_._2.product).distinct().count()

          println("Got " + numRatings + " ratings from "
            + numUsers + " users on " + numMovies + " movies.")

          // Split ratings into train (60%), validation (20%), and test (20%) based on the last digit of the timestamp, add myRatings to train, and cache them

          val numPartitions = 4

          // Training Data
          val training = ratings.filter(x => x._1 < 6)
            .values
            .union(myRatingsRDD)
            .repartition(numPartitions)
            .cache()

          // Validation Data
          val validation = ratings.filter(x => x._1 >= 6 && x._1 < 8)
            .values
            .repartition(numPartitions)
            .cache()

          // Testing Data
          val test = ratings.filter(x => x._1 >= 8).values.cache()

          val numTraining = training.count()
          val numValidation = validation.count()
          val numTest = test.count()

          println("Training: " + numTraining + ", validation: " + numValidation + ", test: " + numTest)

          // Train models and evaluate them on the validation set
          val ranks = List(8, 12)
          val lambdas = List(0.1, 10.0)
          val numIters = List(10, 20)
          var bestModel: Option[MatrixFactorizationModel] = None
          var bestValidationRmse = Double.MaxValue
          var bestRank = 0
          var bestLambda = -1.0
          var bestNumIter = -1
          for (rank <- ranks; lambda <- lambdas; numIter <- numIters) {
            val model = ALS.train(training, rank, numIter, lambda)
            val validationRmse = computeRmse(model, validation, numValidation)
            println("RMSE (validation) = " + validationRmse + " for the model trained with rank = "
              + rank + ", lambda = " + lambda + ", and numIter = " + numIter + ".")
            if (validationRmse < bestValidationRmse) {
              bestModel = Some(model)
              bestValidationRmse = validationRmse
              bestRank = rank
              bestLambda = lambda
              bestNumIter = numIter
            }
          }

          // Evaluate the best model on the test set
          val testRmse = computeRmse(bestModel.get, test, numTest)

          println("The best model was trained with Rank = " + bestRank + ", Lambda = " + bestLambda + ", numIter = " + bestNumIter + ", RMSE on the testSet is " + testRmse + ".")

          // Create a naive baseline and compare it with the best model
          val meanRating = training.union(validation).map(_.rating).mean
          val baselineRmse =
            math.sqrt(test.map(x => (meanRating - x.rating) * (meanRating - x.rating)).mean)
          val improvement = (baselineRmse - testRmse) / baselineRmse * 100
          println("The best model improves the baseline by " + "%1.2f".format(improvement) + "%.")

          // Make personalized recommendations
          val myRatedMovieIds = myRatings.map(_.product).toSet
          val candidates = spark.parallelize(movies.keys.filter(!myRatedMovieIds.contains(_)).toSeq)
          val recommendations = bestModel.get
            .predict(candidates.map((0, _)))
            .collect()
            .sortBy(- _.rating)
            .take(50)

          // Output the predictions
          val moviesRdd = spark.parallelize(recommendations.zipWithIndex.map { case (r, i) => "%2d".format(i) + ": " + movies(r.product) }.toList)
          moviesRdd.saveAsTextFile("hdfs://namenode:8020/output/$className")
        }
      } else {
        println("No command-line arguments provided.")
      }
    } catch {
      case e: Exception => e.printStackTrace()
    } finally {
      spark.stop()
    }
  }
   /** Compute RMSE (Root Mean Squared Error). */
  def computeRmse(model: MatrixFactorizationModel, data: RDD[Rating], n: Long): Double = {
    val predictions: RDD[Rating] = model.predict(data.map(x => (x.user, x.product)))
    val predictionsAndRatings = predictions.map(x => ((x.user, x.product), x.rating))
      .join(data.map(x => ((x.user, x.product), x.rating)))
      .values
    math.sqrt(predictionsAndRatings.map(x => (x._1 - x._2) * (x._1 - x._2)).reduce(_ + _) / n)
  }
}
