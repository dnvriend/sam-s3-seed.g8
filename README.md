![Logo image](img/sbtscalasamlogo_small.png)

# sam-s3-seed.g8
A template project for quickly creating S3 bucket event handlers.

For more information see [sbt-sam](https://github.com/dnvriend/sbt-sam)

## Usage
Create a new template project by typing:

```
sbt new dnvriend/sam-s3-seed.g8
```

## Usage
- To deploy the project type `samDeploy`
- To remove the project type `samRemove`
- To get deployment information like available endpoints and stack information, type `samInfo`

## Event Handler Configuration
Lets create the following event handler for the S3 event source:

```scala
@S3Conf(bucketResourceName = "MyUploadBucket", events = Array("s3:ObjectCreated:*", "s3:ObjectRemoved:*"))
class NotifyBucketEventsHandler extends S3EventHandler {
  override def handle(events: List[S3Event], ctx: SamContext): Unit = {
    println(events)
  }
}
```

Lets create a bucket with the resourceName: `MyUploadBucket` in `/conf/sam.conf`:

```bash
buckets {
  MyUploadBucket {
    name = "dnvriend-upload-bucket"
    access-control = "BucketOwnerFullControl" // AuthenticatedRead | AwsExecRead | BucketOwnerRead | BucketOwnerFullControl | LogDeliveryWrite | Private | PublicRead | PublicReadWrite
    versioning-enabled = false
    cors-enabled = false
    accelerate-enabled = false
    export = false
  }
}
```

The S3Conf annotation refers to the resourceName `MyUploadBucket` in `sam.conf` and this name will be used in the generated
cloudformation script. That is how sbt-sam links resources and event handlers by means of resource names. Next we need to
configure a list of event types that we wish to handle, here we choose to handle all created and removed events. The full
list of events types is available below. 

## The following event types are available:
- s3:ObjectCreated:*
- s3:ObjectCreated:Put
- s3:ObjectCreated:Post
- s3:ObjectCreated:Copy
- s3:ObjectCreated:CompleteMultipartUpload
- s3:ObjectRemoved:*
- s3:ObjectRemoved:Delete
- s3:ObjectRemoved:DeleteMarkerCreated
- s3:ReducedRedundancyLostObject

## Uploading a file
Using the AWS CLI, we can easily upload a file to S3:

```bash
aws s3 cp ~/people.json s3://sam-s3-seed-dev-dnvriend-upload-bucket
```

This results in the following output, when the EventHandler does a println:

```
[info] 2018-01-13T07:36:01.827 - List(S3Event(ObjectCreatedPut,aws:s3,eu-west-1,S3(Object(people.json,Some(7500)),Bucket(arn:aws:s3:::sam-s3-seed-dev-dnvriend-upload-bucket,sam-s3-seed-dev-dnvriend-upload-bucket))))
```

## Reading data from S3 Buckets
To read data from S3 buckets, the s3 event handler can be changed like so:

```scala
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
```

Have fun!