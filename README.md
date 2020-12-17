## Run unit and integration tests (require Docker)
```sh
$ sbt test
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
