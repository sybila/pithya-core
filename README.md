[![Release](https://jitpack.io/v/sybila/biodivine-ctl.svg)](https://jitpack.io/#sybila/biodivine-ctl)
[![Build Status](https://travis-ci.org/sybila/biodivine-ctl.svg?branch=master)](https://travis-ci.org/sybila/biodivine-ctl)
[![License](https://img.shields.io/badge/License-GPL%20v3-blue.svg?style=flat)](https://github.com/sybila/biodivine-ctl/blob/master/LICENSE.txt)

Pithya is a tool for parameter synthesis of ODE-based models and properties based on a hybrid extension of CTL.

## Dependencies

To run Pithya, you need to have **Java 8+** and Microsoft **Z3 4.5.0** 
installed. If your OS is supported, we strongly recommend downloading precompiled
Z3 binaries from **TODO** (Pithya allows you to specify a custom Z3 
location, so you don't necessarily need to have it in your PATH).

## Download Pithya

You can download the latest version of Pithya from our **TODO link**. 
Pithya is written in Java so you don't have to worry 
about an OS-specific version.

## Run

Pithya has one main binary **bin/pithya**. The bin folder also contains other executables,
however, these are used only when pithya operates together with the GUI interface **TODO**, so you
don't need to worry about them (short description of each is given at the end of this section).

### Arguments

#### Input and output

 - ```[-m,--model] filePath``` *required* Path to the .bio file from which the model should be loaded. Detailed
 description of the .bio format can be found here *TODO link*
 - ```[-p,--property] filePath``` *required* Path to the .huctl file from which verified properties are loaded. Detailed
 descrption of the .huctl format can be found here *TODO link*
 - ```[-ro,--result-output] [stdout, stderr, filePath]``` *default: stdout* File/stream to which verification results should be printed. 
*You can use this option to print log and results separately. Note: errors are always printed to stderr.*
 - ```[-r,--result] [human, json]``` *default: human* Output format that is used when printing results.
   * ```human``` Informal text format that should be easily readable to sentient creatures.
   * ```json``` A more formal output format that can be easily parsed by other tools. Description *TODO*.
 - ```[-lo,--log-output] [stdout, stderr, filePath]``` *default: stdout* File/stream to which logging info should be printed. 
*You can use this option to print log and results separately. Note: errors are always printed to stderr.*
 - ```[-l,--log] [none, info, verbose, debug]``` *default: verbose* Amount of log data to print during execution.
   * ```none``` No logging.
   * ```info``` Print coarse verification progress and statistics (started operators, final solver throughput).
   * ```verbose``` Print interactive progress with dynamic throughput statistics (roughly every 2s).
   * ```debug``` Print everything.

#### Verification options

 - ```--parallelism integer``` *default: runtime.avaialableProcessors* The maximum number of threads that are used for parallel
 computation (this is an upper bound, for some specific models/properties, the desired level of parallelism might not be achievable).
 - ```--z3-path filePath``` *default: z3* Relative or absolute path to the z3 command line executable.
 - ```--transition-preprocessing [true, false]``` *default: true* To speed up on-the-fly transition generator, parts of the transition system
 are evaluated before the verification starts. This might not be desirable when the model is very large, but the property is expected to
 require only small amount of explored state space. Use this flag to turn off this preprocessing step.
 - ```--fast-approximation [true, false]``` *default: false* Uses much faster, but not necessarily optimal 
 version of the [Piecewise linear approximation] (https://optimization.mccormick.northwestern.edu/index.php/Piecewise_linear_approximation) 
 when evaluating the model ODEs.
 - ```--create-self-loops [true, false]``` *default: true* Creating selfloops can cause significant overhead even though they have no
 impact on some types of properties (mainly reachability). You can disable selfloops using this switch.

### Other executables

Apart from the main binary, Pithya also contains these separate utilities:
 - ```tractor``` Takes a path to one .bio file as a command line argument, performs the linear approximation and prints 
 resulting .bio model file to standard output. You can use ```tractor``` as a .bio syntax checker.
 - ```combine``` Takes a path to the (already approximated) .bio model and the .huctl property file as command line arguments
 and prints a configuration json file. You can use ```combine``` to verify that your model and property files are valid.

## Build from source

You can naturally build Pithya from source if you're so inclined. After you clone 
the repository, you can run one of these commands in the root folder:

*TODO: find how to give args to run command*

``` 
# On Windows, replace ./gradlew with ./gradlew.bat

# Build Pithya and place unpacked distribution into ./build/install/pithya
./gradlew installDist

# Build Pithya and place compressed distribution into ./build/distributions
./gradlew distZip

# Build Pithya and run it immediately with provided arguments
./gradlew run 
```

(if you have a local gradle installation, you can replace ./gradlew with gradle for faster build)
 
### Project status

Pithya is composed of several independent modules. Here you can find links to them and their current status:

[![Release](https://jitpack.io/v/sybila/ctl-model-checker.svg)](https://jitpack.io/#sybila/ctl-model-checker)
[![Build Status](https://travis-ci.org/sybila/ctl-model-checker.svg?branch=master)](https://travis-ci.org/sybila/ctl-model-checker)
[![codecov.io](https://codecov.io/github/sybila/ctl-model-checker/coverage.svg?branch=master)](https://codecov.io/github/sybila/ctl-model-checker?branch=master)
[CTL Model Checker](https://github.com/sybila/ctl-model-checker)

[![Release](https://jitpack.io/v/sybila/ctl-parser.svg)](https://jitpack.io/#sybila/ctl-parser)
[![Build Status](https://travis-ci.org/sybila/ctl-parser.svg?branch=master)](https://travis-ci.org/sybila/ctl-parser)
[![codecov.io](https://codecov.io/github/sybila/ctl-parser/coverage.svg?branch=master)](https://codecov.io/github/sybila/ctl-parser?branch=master)
[CTL Query Parser](https://github.com/sybila/ctl-parser)

[![Release](https://jitpack.io/v/sybila/ode-generator.svg)](https://jitpack.io/#sybila/ode-generator)
[![Build Status](https://travis-ci.org/sybila/ode-generator.svg?branch=master)](https://travis-ci.org/sybila/ode-generator)
[![codecov.io](https://codecov.io/github/sybila/ode-generator/coverage.svg?branch=master)](https://codecov.io/github/sybila/ode-generator?branch=master)
[ODE State Space Generator](https://github.com/sybila/ode-generator)
