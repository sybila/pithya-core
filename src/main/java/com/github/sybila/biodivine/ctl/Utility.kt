package com.github.sybila.biodivine.ctl

import com.github.sybila.biodivine.CleanFormatter
import com.github.sybila.biodivine.ConsoleHandler
import com.github.sybila.biodivine.rootPackage
import com.github.sybila.checker.Colors
import com.github.sybila.checker.ModelChecker
import com.github.sybila.checker.Node
import com.github.sybila.checker.Nodes
import com.github.sybila.ctl.CTLParser
import com.github.sybila.ode.generator.NodeEncoder
import com.github.sybila.ode.model.Model
import com.github.sybila.ode.model.Parser
import com.github.sybila.ode.model.computeApproximation
import java.io.File
import java.util.logging.FileHandler
import java.util.logging.Level
import java.util.logging.Logger
import java.util.logging.SimpleFormatter

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
    val dest = File(taskRoot, config.file.name)
    if (!dest.exists()) {
        config.file.copyTo(dest, overwrite = true)
    }

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
