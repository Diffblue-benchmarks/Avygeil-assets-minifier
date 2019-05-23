# assets-minifier

## Compiling

JDK 1.8+ is required.

```shell
gradlew shadowJar
```

The output JAR will be located in `build/libs/assets-minifier-all.jar`

## Usage

JRE or JDK 1.8+ is required.

```shell
java -jar assets-minifier.jar <rules> <input folder> [output folder]
```

* `rules`: Name of the rules file used for minifying, **with extension**. First, the program will check for bundled files inside the JAR with the provided name (see below). If not found, this argument is assumed to be a path to a custom file.
* `input folder`: The program will look for source zip archives inside that folder.
* `output folder` (optional): The program will output the minified zip archive as well as temporary files inside that folder (write permission required).

Example: `java -jar assets-minifier.jar jkaserver1.01.rules path/to/server/base out`

Minifies server assets from `path/to/server` using the bundled `jkaserver1.01.rules` and writes output to `./out` in the working directory.

### Bundled rules files

* `jkaserver1.01.rules`: generic all purpose JKA server (~104MB)
* `jkaserver.ctf.1.01.rules`: barebone JKA server with just CTF related assets (~12MB)
* `jkaserver.siege.1.01.rules`: barebone JKA server with just Siege related assets (~8MB)

### Custom rules file

Rules files must contain one rule per line:

* `OutFilename <name with extension>` (required): Name of the minified archive created in the output folder.
* `InputFile <md5 hash> <filename with extension>`: Adds the specified archive as a source archive for the minifier. Setting 0 as a hash skips the md5 check for this file. If no input file is specified at all, all valid archives from the input folder will be used as source.
* `EntryWhitelist <wildcard matcher>`: Filenames inside archives matching this will be included, unless blacklisted (see below).
* `EntryBlacklist <wildcard matcher>`: Filenames inside archives matching this will always be excluded.
