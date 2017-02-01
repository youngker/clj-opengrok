# clj-opengrok

Command Line Interface for OpenGrok

## Requirements

* Java
* Exuberant Ctags

## Installation

* Download [clj-opengrok-0.13.0](https://github.com/youngker/clj-opengrok/releases)

```shell
$ cp clj-opengrok /usr/local/bin
                or
$ copy clj-opengrok.bat C:\path\to\bin
```

## Usage

<img align="center" src="https://raw.github.com/youngker/clj-opengrok/master/img/usage.png">

### Indexing

```shell
$ clj-opengrok index -s /path/to/project -e
```

### Searching

```shell
$ clj-opengrok search -R /path/to/project/.opengrok/configuration.xml -f text
```

## Building

* Executable jar

```shell
$ ./build
$ cp clj-opengrok /usr/local/bin
                or
$ copy clj-opengrok.bat C:\path\to\bin
```

## License

Copyright (C) 2017 Youngjoo Lee

Author: Youngjoo Lee <youngker@gmail.com>

Distributed under the Eclipse Public License, the same as Clojure.
