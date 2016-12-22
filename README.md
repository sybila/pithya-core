[![Release](https://jitpack.io/v/sybila/biodivine-ctl.svg)](https://jitpack.io/#sybila/biodivine-ctl)
[![Build Status](https://travis-ci.org/sybila/biodivine-ctl.svg?branch=master)](https://travis-ci.org/sybila/biodivine-ctl)
[![codecov.io](https://codecov.io/github/sybila/biodivine-ctl/coverage.svg?branch=master)](https://codecov.io/github/sybila/biodivine-ctl?branch=master)
[![License](https://img.shields.io/badge/License-GPL%20v3-blue.svg?style=flat)](https://github.com/sybila/biodivine-ctl/blob/master/LICENSE.txt)
[![Kotlin](https://img.shields.io/badge/kotlin-1.0.1-blue.svg)](http://kotlinlang.org)

BioDivine is a tool for distributed or parallel model checking of biological models with parameters.

This repository should provide a frontend interface for running experiments using BioDivine engines and
states space generators. It's currently under construction with a beta release coming soon.

### How to build

Make sure you have Z3 prover installed and included in your path.

Clone the repository and run ./gradlew installDist (or ./gradlew.bat installDist on Windows). Biodivine installation will be created in build/install/biodivine-ctl. You can then move this folder to your desired location.

### How to use

Biodivine installation contains bin folder with three executables:

tractor - takes one argument with a path to a .bio model and prints a piece-wise multi affine abstraction of this model on stdout.

combine - takes two arguments: piece-wise multi affine model and .ctl property file and prints a json configuration file to stdout.

biodivine-ctl - main binary, takse the json config file as command line argument and prints result json.

### Project status

BioDivine is composed of several independent modules. Here you can find links to them and their status:

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
