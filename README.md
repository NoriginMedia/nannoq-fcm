# DISCONTINUED JAVA LEGACY - Use Kotlin version @ [Nannoq-Tools](https://github.com/NoriginMedia/nannoq-tools)

# Nannoq FCM

[![Build Status](https://www.tomrom.net/buildStatus/icon?job=nannoq-tools/master)](https://www.tomrom.net/job/nannoq-tools/job/master/)

nannoq-fcm is a XMPP server implementation for use with Firebase Cloud Messaging with all features, for a Vert.x environment.

It supports:
 - Topics
 - Direct Messages (Down and Upstream)
 - Device Groups
 - Device Registrations

### Prerequisites

Vert.x >= 3.5.0

Java >= 1.8

Maven

Redis

## Installing

mvn clean package -Dgpg.skip=true

### Running the tests

mvn clean test -Dgpg.skip=true

### Running the integration tests

mvn clean verify -Dgpg.skip=true

## Usage

First install with either Maven:

```xml
<dependency>
    <groupId>com.nannoq</groupId>
    <artifactId>fcm</artifactId>
    <version>1.0.5</version>
</dependency>
```

or Gradle:

```groovy
dependencies {
    compile group: 'nannoq.com:fcm:1.0.5’
}
```

### Implementation and Use

Please consult the [Wiki](https://github.com/NoriginMedia/nannoq-fcm/wiki) for guides on implementations and use.

## Contributing

Please read [CONTRIBUTING.md](https://github.com/NoriginMedia/nannoq-fcm/blob/master/CONTRIBUTING.md) for details on our code of conduct, and the process for submitting pull requests to us.

## Versioning

We use [SemVer](http://semver.org/) for versioning. For the versions available, see the [tags on this repository](https://github.com/NoriginMedia/nannoq-fcm/tags)

## Authors

* **Anders Mikkelsen** - *Initial work* - [Norigin Media](http://noriginmedia.com/)

See also the list of [contributors](https://github.com/NoriginMedia/nannoq-fcm/contributors) who participated in this project.

## License

This project is licensed under the MIT License - see the [LICENSE.md](https://github.com/NoriginMedia/nannoq-fcm/blob/master/LICENSE) file for details
