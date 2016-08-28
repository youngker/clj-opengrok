# clj-opengrok

Command Line Interface for OpenGrok

## Requirements

* Java 1.8

## Installation

**TODO**

## Usage

### Indexing

```shell
$ clj-opengrok index -s /path/to/project -P
```

### Searching

```shell
$ clj-opengrok search -R /path/to/project/.opengrok/configuration.xml -f text
```

## Building

* Install lein-localrepo [https://github.com/kumarshantanu/lein-localrepo](https://github.com/kumarshantanu/lein-localrepo)

* Download OpenGrok latest release [https://github.com/OpenGrok/OpenGrok/releases](https://github.com/OpenGrok/OpenGrok/releases)

* Install opengrok.jar

```shell
$ cd opengrok-0.12.1.5/lib
$ lein localrepo install opengrok.jar org.opensolaris/opengrok 0.12.1.5
```

* Install jrcs.jar

```shell
$ cd opengrok-0.12.1.5/lib/lib
$ lein localrepo install jrcs.jar org.apache.commons/jrcs 0.12.1.5
```

* Executable jar (requires shell)

```shell
$ cd clj-opengrok
$ ./build
$ cp clj-opengrok /usr/local/bin
```


## License

Copyright (C) 2016 Youngjoo Lee

Author: Youngjoo Lee <youngker@gmail.com>

Distributed under the Eclipse Public License, the same as Clojure.
