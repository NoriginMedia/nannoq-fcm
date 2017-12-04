## Welcome to Nannoq Auth

nannoq-fcm is a XMPP server implementation for use with Firebase Cloud Messaging with all features, for a Vert.x environment.

### Prerequisites

Vert.x >= 3.5.0

Java >= 1.8

Maven

### Installing

mvn clean package -Dgpg.skip=true

## Running the tests

mvn clean test -Dgpg.skip=true

## Running the integration tests

mvn clean verify -Dgpg.skip=true
