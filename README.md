[![Review Assignment Due Date](https://classroom.github.com/assets/deadline-readme-button-22041afd0340ce965d47ae6ef1cefeee28c7c493a6346c4f15d667ab976d596c.svg)](https://classroom.github.com/a/51CAWZT5)
Sismics Rudra's Subscription Service (RSS) - Reader: Project 1
==============

**Do not modify this README. Use the docs directory for anything you might want to submit**

What is RSS-Reader?
---------------

RSS-Reader is an open source, Web-based aggregator of content served by Web Feeds (RSS, Atom).

RSS-Reader is written in Java, and may be run on any operating system with Java support.

Features
--------


- Supports RSS and Atom standards
- Organize your feeds into categories and keep track of your favorite articles
- Supports Web based and mobile user interfaces
- Keyboard shortcuts
- RESTful Web API
- Full text search
- OPML import / export
- Skinnable
- Android application

License
-------

RSS-Reader is released under the terms of the GPL license. See `COPYING` for more
information or see <http://opensource.org/licenses/GPL-2.0>.

<!-- How to run
------------------------------------

RSS-Reader is packaged in several convenient formats. You can download an installer for your system on the [Download Page](https://www.sismics.com/reader/#!/download).

If you use Docker, you can try Reader easily with the following command :

    docker-compose -p reader -f reader-distribution-docker/docker-compose.yml up -->

# Building RSS-Reader from source
------------------------------------

Prerequisites: JDK 8, Maven 3

### Changing Java Version
You will need two versions of Java to work on this project. RSS-Reader requires JDK 8 and Sonarqube requires Java 11. You will need to change Java versions for working on different parts. The easiest way to do this is to install both versions for your platform and then changing the SDK for the project in IntelliJ IDEA. 

### For Globally Changing Java Version (Only on Ubuntu)
* Run the following command and select the version of Java you want to use.

```bash
sudo update-alternatives --config java
```
* Similarly for javac.

```bash
sudo update-alternatives --config javac
```
> Make sure you set the same version for both.

### Or you can follow this method for Updating in Specific Runtime (Mac & Linux)
* Instead of globally updating your Java version, it is better to temporarily change the Java version i.e. for as long as the terminal is open.
* This is done by setting the path variable JAVA_HOME to the version of Java you want to use.
* The command would be export `JAVA_HOME=<path to java installation>` for Mac and Linux.

> The paths mentioned here are only sample paths. Make sure you find out the actual path for your JDK and use that.

**On Mac**:
It is recommended to use homebrew to manage manage different openjdk versions. This is an example command and the actual command would look something like this -

```bash 
export JAVA_HOME=/Library/Java/JavaVirtualMachines/<jdk-version-something>/Contents/Home/
```

**On Linux**:
This is an example command and the actual command would look something like this -

```bash 
export JAVA_HOME=/usr/lib/jvm/<jdk-version-something>
```

**On Windows**:
Windows users, this is your cross to bear. [Here's](https://confluence.atlassian.com/doc/setting-the-java_home-variable-in-windows-8895.html) a guide that might be of use. Again, feel free to contact us for any help, but try to use Linux or WSL first if possible. 

### Reader is organized in several Maven modules:

  - reader-core
  - reader-web
  - reader-web-common
  - reader-android

<!-- First off, clone the repository: `git clone git@github.com:Meghanatedla/RSS-Reader.git` or download the sources from GitHub. -->

#### Launch the build

From the root directory:

    mvn clean -DskipTests install -e

#### Run a stand-alone version

From the `reader-web` directory:

    mvn jetty:run

#### Navigate to the following URL on your browser:

http://localhost:8080/reader-web/src/#/wizard

Use default credentials as admin(username) and admin(password).

#### [Optional] Build a .war to deploy to your servlet container

From the `reader-web` directory:

```bash
mvn -Pprod -DskipTests clean install
```

You will get your deployable WAR in the `target` directory.

#### [Optional] Build the Android app

Prerequisites :
  - Gradle
  - Android SDK
  - Environment variables pointing to the keystore (see `build.gradle`)

Then, from the `reader-android` directory:

```bash 
gradlew build
```
    
The generated APK will be in `app/build/apk/app-release.apk`

---
**Note**: Mr. Rudra Dhar is not associated with the development of this project (code or SE project 1). Any code smell, design smell, bug or fault is through no fault of Mr. Rudra Dhar.