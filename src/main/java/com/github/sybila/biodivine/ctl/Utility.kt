package com.github.sybila.biodivine.ctl

/*
fun getGlobalLogger(
        consoleLogLevel: Level,
        taskRoot: File,
        name: String
): Logger {
    val logger = Logger.getLogger(rootPackage)
    logger.useParentHandlers = false
    logger.addHandler(ConsoleHandler().apply {
        this.level = consoleLogLevel
        this.formatter = CleanFormatter()
    })
    logger.addHandler(FileHandler("${taskRoot.absolutePath}/$name.log").apply {
        this.formatter = SimpleFormatter()  //NO XML!
    })
    return logger
}

fun loadModel(
        config: ODEModelConfig,
        taskRoot: File,
        logger: Logger
): Model {
    //copy model file into task folder
    /*TODO Stale file handle on pheme
    val dest = File(taskRoot, config.file.name)
    if (!dest.exists()) {
        config.file.copyTo(dest, overwrite = true)
    }*/

    val start = System.currentTimeMillis()
    val model = Parser().parse(config.file).apply {
        logger.info("Model parsing finished. Running approximation...")
    }.computeApproximation(
            fast = config.fastApproximation,
            cutToRange = config.cutToRange
    )
    logger.info("Model approximation finished. Elapsed time: ${System.currentTimeMillis() - start}ms")
    return model
}

fun <N: Node, C: Colors<C>> verify(property: PropertyConfig, parser: CTLParser, checker: ModelChecker<N, C>, logger: Logger): Nodes<N, C> {

    when {
        property.verify != null && property.formula != null ->
            error("Invalid property. Can't specify inlined formula and verify at the same time: $property")
        property.verify != null && property.file == null ->
            error("Can't resolve ${property.verify}, no CTL file provided")
        property.verify == null && property.formula == null ->
            error("Invalid property. Missing a formula or verify clause. $property")
    }

    val f = if (property.verify != null) {
        val formulas = parser.parse(property.file!!)
        formulas[property.verify] ?: error("Property ${property.verify} not found in file ${property.file}")
    } else if (property.file != null) {
        val formulas = parser.parse("""
                    #include "${property.file.absolutePath}"
                    myLongPropertyNameThatNoOneWillUse = ${property.formula}
                """)
        formulas["myLongPropertyNameThatNoOneWillUse"]!!
    } else {
        parser.formula(property.formula!!)
    }

    logger.info("Start verification of $f")

    val checkStart = System.currentTimeMillis()
    val results = checker.verify(f)

    logger.info("Verification finished, elapsed time: ${System.currentTimeMillis() - checkStart}ms")

    return results
}
*/