[![Build Status](https://travis-ci.org/centic9/commons-audio.svg)](https://travis-ci.org/centic9/commons-audio) [![Gradle Status](https://gradleupdate.appspot.com/centic9/commons-audio/status.svg?branch=master)](https://gradleupdate.appspot.com/centic9/commons-audio/status)
[![Release](https://img.shields.io/github/v/release/centic9/commons-audio.svg)](https://github.com/centic9/commons-audio/releases)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/org.dstadler/commons-audio/badge.svg?style=flat)](https://maven-badges.herokuapp.com/maven-central/org.dstadler/commons-audio) [![Maven Central](https://img.shields.io/maven-central/v/org.dstadler/commons-audio.svg)](https://maven-badges.herokuapp.com/maven-central/org.dstadler/commons-audio)

This is a small library of code-pieces that I find useful when developing tools and applications around handling of
audio, playing sound, downloading audio from certain websites, ...

## Contents

Here an (incomplete) list of bits and pieces in this lib:
* A simple AudioPlayer interface and implementations via JLayer, MP3-SPI and TarsosDSP libraries
* Interfaces for buffering audio data in a flexible SeekableRingBuffer
* Downloading ranges of audio-streams from local files or HTTP
* Accessing information about sessions of the Austrian radio station FM4
* An implementation of the SeekableRingBuffer which also provides persisting to disk
* A system for playing sound at different tempo via the TarsosDSP libraries for stretching/condensing audio on-the-fly
* A basic Stream class for holding information about streams that are played  

## Use it

### Gradle

    compile 'org.dstadler:commons-audio:1.+'

## Change it

### Grab it

    git clone git://github.com/centic9/commons-audio

### Build it and run tests

	cd commons-audio
	./gradlew check jacocoTestReport

### Release it

    ./gradlew --console=plain release && ./gradlew closeAndReleaseRepository
    
* This should automatically release the new version on MavenCentral
* Afterwards go to https://github.com/centic9/commons-audio/releases and add release-notes

#### Licensing
* commons-audio is licensed under the [BSD 2-Clause License].

[BSD 2-Clause License]: https://www.opensource.org/licenses/bsd-license.php
