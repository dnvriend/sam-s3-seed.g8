package $package$

import java.io.InputStream

import com.github.dnvriend.lambda.S3EventName.ObjectCreatedPut
import com.github.dnvriend.lambda._
import com.github.dnvriend.lambda.annotation.S3Conf
import com.github.dnvriend.s3.GetS3Object

import scala.io.Source
import scalaz.Disjunction

@S3Conf(bucketResourceName = "MyUploadBucket", events = Array("s3:ObjectCreated:*", "s3:ObjectRemoved:*"))
class NotifyBucketEventsHandler extends S3EventHandler {

  /**
    * Assumes the input stream is encoded as UTF-8 characters, reads the lines in memory.
    * It is advisable to first check how large the file is, else process the file as a stream
    */
  def readLinesToList(maybeInputStream: Disjunction[Throwable, InputStream]): List[String] = {
    println("maybeInputStream: " + maybeInputStream)
    maybeInputStream.fold(t => List("error: " + t.getMessage), is =>
      Source.fromInputStream(is).getLines().toList
    )
  }

  override def handle(events: List[S3Event], ctx: SamContext): Unit = {
    println("Received: " + events)
    val allLinesFromAllFiles: List[String] = events.collect {
      case S3Event(ObjectCreatedPut, eventSource, awsRegion, S3(Object(key, maybeSize), Bucket(arn, bucketName))) =>
        println("Processing: " + bucketName + " -> " + key)
        GetS3Object(bucketName, key)
    }.flatMap(_.getInputStreamManagedD(readLinesToList))

    allLinesFromAllFiles foreach println
  }
}