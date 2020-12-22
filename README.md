## Run unit and integration tests (require Docker)
```sh
$ sbt test
[info] DockerBlackBoxTest:
[info] - Program should successfully download and upload all entries
[info] DummyBlackBoxTest:
[info] - Program should successfully download and upload all entries
[info] NegativeTest:
[info] - AppLogic.processAndUpload should fail if download status is not successful
[info] - AppLogic.processAndUpload should fail if upload status is not successful
[info] ScalaTest
[info] Run completed in 19 seconds, 839 milliseconds.
[info] Total number of tests run: 4
[info] Suites: completed 3, aborted 0
[info] Tests: succeeded 4, failed 0, canceled 0, ignored 0, pending 0
[info] All tests passed.
[info] Passed: Total 4, Failed 0, Errors 0, Passed 4
```
## Package provided test service for integration tests
```sh
$ sbt provided-test-service/docker:publish
```
## Run provided test service
```sh
$ java -jar provided-test-service/lib/coding-challenge.jar
```
or with Sbt
```sh
$ sbt provided-test-service/run
```
or in Docker
```sh
$ sbt provided-test-service/docker:publishLocal && \
    docker run --rm -p 8080:8080 provided-test-service:1.0-SNAPSHOT
```
## Developing in Sbt
```sh
$ sbt
> ~reStart
```
