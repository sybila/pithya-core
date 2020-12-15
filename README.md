[![Build Status](https://travis-ci.org/sybila/pithya-core.svg?branch=master)](https://travis-ci.org/sybila/pithya-core)
[![License](https://img.shields.io/badge/License-GPL%20v3-blue.svg?style=flat)](https://github.com/sybila/biodivine-ctl/blob/master/LICENSE.txt)

Pithya is a tool for parameter synthesis of ODE-based models and properties based on a hybrid extension of CTL.

## Online demo

To try Pithya online, visit [pithya.fi.muni.cz](https://pithya.fi.muni.cz/). In case of any problems/questions, feel free to contact us at [sybila@fi.muni.cz](mailto:sybila@fi.muni.cz).

## Dependencies

To run Pithya, you need to have **Java 8+** and Microsoft **Z3 4.5.0** 
installed. If your OS is supported, we strongly recommend downloading precompiled
Z3 binaries from github (Pithya allows you to specify a custom Z3 
location, so you don't necessarily need to have it in your PATH, but we recommend doing that anyway).

We strongly recommend to use Pithya with a GUI that is developed as a separate project and accessible at https://github.com/sybila/pithya-gui. 

## Download Pithya

You can download the latest version of Pithya from the releases. 

## Run

Pithya has one main binary **bin/pithya**. The bin folder also contains other executables,
however, these are used only when pithya operates together with the GUI interface, so you
don't need to worry about them.

### Arguments

This is desription of arguments for parameter synthesis. Arguments for component analysis are described in the corresponding [repo](https://github.com/sybila/terminal-components).

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
 - ```--fast-approximation [true, false]``` *default: false* Uses much faster, but not necessarily optimal 
 version of the [Piecewise linear approximation] (https://optimization.mccormick.northwestern.edu/index.php/Piecewise_linear_approximation) 
 when evaluating the model ODEs.
 - ```--create-self-loops [true, false]``` *default: true* Creating selfloops can cause significant overhead even though they have no
 impact on some types of properties (mainly reachability). You can disable selfloops using this switch.
## Build from source

You can naturally build Pithya from source if you're so inclined. After you clone 
the repository, you can run one of these commands in the root folder:

``` 
# On Windows, replace ./gradlew with ./gradlew.bat

# Build Pithya and place unpacked distribution into ./build/install/pithya
./gradlew installDist

# Build Pithya and place compressed distribution into ./build/distributions
./gradlew distZip
```

(if you have a local gradle installation, you can replace ./gradlew with gradle for faster build)
 
### Project status

Pithya is composed of several independent modules. Here you can find links to them and their current status:

[![Release](https://jitpack.io/v/sybila/ctl-model-checker.svg)](https://jitpack.io/#sybila/ctl-model-checker)
[![Build Status](https://travis-ci.org/sybila/ctl-model-checker.svg?branch=master)](https://travis-ci.org/sybila/ctl-model-checker)
[![codecov.io](https://codecov.io/github/sybila/ctl-model-checker/coverage.svg?branch=master)](https://codecov.io/github/sybila/ctl-model-checker?branch=master)
[CTL Model Checker](https://github.com/sybila/ctl-model-checker)

[![Release](https://jitpack.io/v/sybila/huctl.svg)](https://jitpack.io/#sybila/huctl)
[![Build Status](https://travis-ci.org/sybila/huctl.svg?branch=master)](https://travis-ci.org/sybila/huctl)
[![codecov.io](https://codecov.io/github/sybila/huctl/coverage.svg?branch=master)](https://codecov.io/github/sybila/huctl?branch=master)
[HUCTL Query Parser](https://github.com/sybila/huctl)

[![Release](https://jitpack.io/v/sybila/ode-generator.svg)](https://jitpack.io/#sybila/ode-generator)
[![Build Status](https://travis-ci.org/sybila/ode-generator.svg?branch=master)](https://travis-ci.org/sybila/ode-generator)
[![codecov.io](https://codecov.io/github/sybila/ode-generator/coverage.svg?branch=master)](https://codecov.io/github/sybila/ode-generator?branch=master)
[ODE State Space Generator](https://github.com/sybila/ode-generator)

[![Release](https://jitpack.io/v/sybila/terminal-components.svg)](https://jitpack.io/#sybila/terminal-components)
[![Build Status](https://travis-ci.org/sybila/terminal-components.svg?branch=master)](https://travis-ci.org/sybila/terminal-components)
[Terminal Components Analysis](https://github.com/sybila/terminal-components)
