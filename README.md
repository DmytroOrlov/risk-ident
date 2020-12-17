## Run unit and integration tests (require Docker)
```sh
$ sbt test
[info] DummyBlackBoxTest:
[info] - UploaderLogic should successfully download and upload all entries
[info] DockerBlackBoxTest:
[info] - UploaderLogic should successfully download and upload all entries
[info] Tests: succeeded 2, failed 0, canceled 0, ignored 0, pending 0
[info] All tests passed.
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
