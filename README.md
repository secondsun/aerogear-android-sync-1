# AeroGear Android Sync [![Build Status](https://travis-ci.org/aerogear/aerogear-android-sync.png)](https://travis-ci.org/aerogear/aerogear-android-sync)

AeroGear's Android libraries were built as jar and aar packages using [Maven](http://maven.apache.org/) and the [android-maven-plugin](https://github.com/jayway/maven-android-plugin). The project follows the standard Maven layout so it can be imported directly into most IDEs as a Maven project.

## Sync

This module contains a client implementations that uses the aerogear-sync-server [netty-client](https://github.com/aerogear/aerogear-sync-server/tree/master/client/client-netty)


|                 | Project Info  |
| --------------- | ------------- |
| License:        | Apache License, Version 2.0  |
| Build:          | Maven  |
| Documentation:  | http://aerogear.org/docs/guides/aerogear-android/  |
| Issue tracker:  | https://issues.jboss.org/browse/AGDROID  |
| Mailing lists:  | [aerogear-users](http://aerogear-users.1116366.n5.nabble.com/) ([subscribe](https://lists.jboss.org/mailman/listinfo/aerogear-users))  |
|                 | [aerogear-dev](http://aerogear-dev.1069024.n5.nabble.com/) ([subscribe](https://lists.jboss.org/mailman/listinfo/aerogear-dev)) 

## Building

Please take a look at the [step by step on our website](http://aerogear.org/docs/guides/aerogear-android/how-to-build-aerogear-android/). 

*The following dependencies are required to build this project:*

* [aerogear-sync-server](https://github.com/aerogear/aerogear-sync-server) 

## Usage

There are two supported ways of developing apps using AeroGear for Android: Android Studio and Maven.

### Android Studio

Add to your application's `build.gradle` file

```
dependencies {
  compile 'org.jboss.aerogear:aerogear-android-sync:0.0.1-SNAPSHOT'
}
```

### Maven

Include the following dependencies in your project's `pom.xml`


```
<dependency>
  <groupId>org.jboss.aerogear</groupId>
  <artifactId>aerogear-android-sync</artifactId>
  <version>0.0.1-SNAPSHOT</version>
  <scope>provided</scope>
  <type>jar</type>
</dependency>

<dependency>
  <groupId>org.jboss.aerogear</groupId>
  <artifactId>aerogear-android-sync</artifactId>
  <version>0.0.1-SNAPSHOT</version>
  <type>aar</type>
</dependency>
```

## Documentation

For more details about that please consult [our documentation](http://aerogear.org/docs/guides/aerogear-android).

## Demo apps

Take a look in our demo apps

* [SyncDemo](https://github.com/aerogear/aerogear-android-cookbook/blob/master/SyncDemo)

## Development

If you would like to help develop AeroGear you can join our [developer's mailing list](https://lists.jboss.org/mailman/listinfo/aerogear-dev), join #aerogear on Freenode, or shout at us on Twitter @aerogears.

Also takes some time and skim the [contributor guide](http://aerogear.org/docs/guides/Contributing/)

## Questions?

Join our [user mailing list](https://lists.jboss.org/mailman/listinfo/aerogear-users) for any questions or help! We really hope you enjoy app development with AeroGear!

## Found a bug?

If you found a bug please create a ticket for us on [Jira](https://issues.jboss.org/browse/AGDROID) with some steps to reproduce it.

