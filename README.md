[![Build Status](https://github.com/centic9/commons-audio/actions/workflows/gradle-build.yml/badge.svg)](https://github.com/centic9/commons-audio/actions)
[![Gradle Status](https://gradleupdate.appspot.com/centic9/commons-audio/status.svg?branch=master)](https://gradleupdate.appspot.com/centic9/commons-audio/status)
[![Release](https://img.shields.io/github/release/centic9/commons-audio.svg)](https://github.com/centic9/commons-audio/releases)
[![GitHub release](https://img.shields.io/github/release/centic9/commons-audio.svg?label=changelog)](https://github.com/centic9/commons-audio/releases/latest)
[![Tag](https://img.shields.io/github/tag/centic9/commons-audio.svg)](https://github.com/centic9/commons-audio/tags)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/org.dstadler/commons-audio/badge.svg?style=flat)](https://maven-badges.herokuapp.com/maven-central/org.dstadler/commons-audio) 
[![Maven Central](https://img.shields.io/maven-central/v/org.dstadler/commons-audio.svg)](https://maven-badges.herokuapp.com/maven-central/org.dstadler/commons-audio)

This is a small library of code-pieces that I find useful when developing tools and applications around handling of
audio, playing sound, downloading audio from certain websites, ...

## Contents

Here an (incomplete) list of bits and pieces in this lib:
* A simple AudioPlayer interface and implementations via JLayer, MP3/OGG-SPI and TarsosDSP libraries
* Interfaces for buffering audio data in a flexible SeekableRingBuffer
* Downloading ranges of audio-streams from local files or HTTP
* Accessing information about sessions of the Austrian radio station FM4
* An implementation of the SeekableRingBuffer which also provides persisting to disk
* A system for playing sound at different tempo via the TarsosDSP libraries for stretching/condensing audio on-the-fly
* A basic Stream class for holding information about streams that are played
* Extensions to PipedInputStream which help with clearing and flushing the internal buffer

There is also a simple commandline audio-player `ExamplePlayer` which shows how to use the components to
provide proper buffered playback of audio from either local files or audio streams fetched via HTTP.   

## Usage

The SeekableRingBuffer and it's implementations are at the core of some of my audio applications, 
they allow to have a full-featured ring-buffer for audio-data in the application and for example 
have one thread fetch the audio data from somewhere, e.g. from a file or from an HTTP download or a
live stream of some radio-station. Another thread can read from the stream and handle sending the audio
to the local player. 

This way fetching the audio-data and playing it are nicely decoupled with flexible 
buffering which not only allows playing the live audio (with only a small lag), but also allows to 
skip forward and backwards in the buffered audio.

Reading will wait if the buffer is full, i.e. if reading is faster than writing (which it usually should be).

For live-streams, naturally reading will be as fast as playing, but previously read data will still be available
for skipping backwards.

Writing will wait for new data if the buffer is empty, i.e. if writing is faster, which may indicate some problem
with the stream or a slow internet connection.

Via this buffering and the support for playing audio at different speed you can build some very powerful
applications, e.g. playing a radio-station, but allowing the user to skip forward/backward, e.g. to skip
across ads or if the content is not interesting.

You can play the audio a bit slower if the play-position is near the "head" of the buffer or playing a bit 
faster if the play position is at the "tail", thus ensuring that over time there is always some buffer
available for skipping back and forward a few minutes.

## Using it

### Add it to your project as Gradle dependency

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
* Afterwards go to the [Github releases page](https://github.com/centic9/commons-audio/releases) and add release-notes

## Support this project

If you find this library useful and would like to support it, you can [Sponsor the author](https://github.com/sponsors/centic9)

## Licensing

* commons-audio is licensed under the [BSD 2-Clause License].

[BSD 2-Clause License]: https://www.opensource.org/licenses/bsd-license.php
