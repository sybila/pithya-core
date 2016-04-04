package com.github.sybila.biodivine.ctl

import com.github.sybila.biodivine.YamlMap
import com.github.sybila.biodivine.c
import com.github.sybila.ctl.CTLParser
import com.github.sybila.ctl.untilNormalForm
import java.io.File
import java.util.logging.Level
import java.util.logging.Logger

data class CTLParameterEstimationConfig(
        val maxMemory: Int,
        val timeout: Int,
        val jobQueue: JobQueueConfig,
        val communicator: CommunicatorConfig,
        val partitioning: PartitioningConfig,
        val parser: CTLParser.Configuration,
        val model: ModelConfig,
        val checker: CheckerConfig,
        val colors: ColorsConfig
) {
    constructor(config: YamlMap) : this(
            config.getInt(c.maxMemory, 1024),
            config.getInt(c.timeout, -1),
            config.loadJobQueueConfig(),
            config.loadCommunicatorConfig(),
            config.loadPartitioningConfig(),
            config.loadParserConfig(),
            config.loadModelConfig(),
            config.loadCheckerConfig(),
            config.loadColorsConfig()
    )
}

fun YamlMap.loadPartitioningConfig(): PartitioningConfig = this.getAny(c.partitioning)?.run {
    when (this) {
        c.uniform -> UniformPartitionConfig()
        c.hash -> HashPartitionConfig()
        c.slice -> SlicePartitionConfig()
        c.block -> BlockPartitionConfig()
        is Map<*,*> -> {
            val partitionConfig = YamlMap(this)
            when (partitionConfig.getString(c.type)) {
                c.uniform -> UniformPartitionConfig()
                c.hash -> HashPartitionConfig()
                c.slice -> SlicePartitionConfig()
                c.block -> BlockPartitionConfig(partitionConfig)
                else -> throw IllegalArgumentException("Unsupported partition function: $this")
            }
        }
        else -> throw IllegalArgumentException("Unsupported partition function: $this")
    }
} ?: UniformPartitionConfig()

fun YamlMap.loadJobQueueConfig(): JobQueueConfig = this.getAny(c.jobQueue)?.run {
    when (this) {
        c.blockingQueue -> BlockingJobQueueConfig()
        c.mergeQueue -> MergeQueueConfig()
        is Map<*,*> -> {
            val queueConfig = YamlMap(this)
            when (queueConfig.getString(c.type)) {
                c.blockingQueue -> BlockingJobQueueConfig(queueConfig)
                c.mergeQueue -> MergeQueueConfig(queueConfig)
                else -> throw IllegalArgumentException("Unsupported job queue config: $this")
            }
        }
        else -> throw IllegalArgumentException("Unsupported job queue config: $this")
    }
} ?: BlockingJobQueueConfig()

fun YamlMap.loadCommunicatorConfig(): CommunicatorConfig = this.getAny(c.communicator)?.run {
    when (this) {
        c.noCommunicator -> NoCommunicatorConfig()
        c.sharedMemory -> SharedCommunicatorConfig()
        c.mpjLocal -> MPJLocalCommunicatorConfig()
        c.mpjCluster -> throw IllegalArgumentException("You have to provide a worker node list in cluster configuration!")
        is Map<*,*> -> {
            val commConfig = YamlMap(this)
            when (commConfig.getString(c.type)) {
                c.noCommunicator -> NoCommunicatorConfig()
                c.sharedMemory -> SharedCommunicatorConfig(commConfig)
                c.mpjLocal -> MPJLocalCommunicatorConfig(commConfig)
                c.mpjCluster -> MPJClusterCommunicatorConfig(commConfig)
                else -> throw IllegalArgumentException("Unsupported communicator config: $this")
            }
        }
        else -> throw IllegalArgumentException("Unsupported communicator config: $this")
    }
} ?: NoCommunicatorConfig()


fun YamlMap.loadParserConfig(): CTLParser.Configuration {
    val parserConfig = this.getMap(c.ctlParser)
    val normalForm = parserConfig.getString(c.normalForm, c.until)
    val logLevel = this.getLogLevel(c.logLevel, Level.INFO)
    return CTLParser.Configuration(
            when (normalForm) {
                c.until -> untilNormalForm
                c.none -> null
                else -> error("Unknown normal form: $normalForm")
            }, this.getBoolean(c.optimize, true),
            Logger.getLogger(CTLParser::class.java.canonicalName).apply { level = logLevel }
    )
}

fun YamlMap.loadModelConfig(): ModelConfig = this.getMap(c.model).run {
        when (this.getString(c.type, c.ODE)) {
            c.ODE -> ODEModelConfig(this)
            else -> throw IllegalArgumentException("Unsupported model type: $this")
        }
    }

fun YamlMap.loadCheckerConfig(): CheckerConfig = this.getMap(c.checker).run {
        CheckerConfig(this)
    }

fun YamlMap.loadColorsConfig(): ColorsConfig = this.getAny(c.colors)?.run {
        when (this) {
            c.rectangular -> RectangularColorsConfig()
            c.smt -> SMTColorsConfig()
            is Map<*,*> -> {
                val colorsConfig = YamlMap(this)
                when (colorsConfig.getString(c.type, c.rectangular)) {
                    c.rectangular -> RectangularColorsConfig()
                    c.smt -> SMTColorsConfig()
                    else -> throw IllegalArgumentException("Unsupported colors type: $this")
                }
            }
            else -> throw IllegalArgumentException("Unsupported colors type: $this")
        }
    } ?: RectangularColorsConfig()

/*
    Communicator configuration
 */

interface CommunicatorConfig

class NoCommunicatorConfig : CommunicatorConfig

data class SharedCommunicatorConfig(
        val workers: Int = 1
) : CommunicatorConfig {
    constructor(config: YamlMap) : this (config.getInt(c.workers, 1))
}

data class MPJLocalCommunicatorConfig(
        val workers: Int = 1,
        val mpjHome: File? = null,
        val logLevel: Level = Level.INFO
) : CommunicatorConfig {
    constructor(config: YamlMap) : this (
            config.getInt(c.workers, 1),
            config.getFile(c.mpjHome),
            config.getLogLevel(c.logLevel, Level.INFO)
    )
}

data class MPJClusterCommunicatorConfig(
        val mpjHome: File? = null,
        val logLevel: Level = Level.INFO,
        val hosts: List<String>,
        val portRange: String
) : CommunicatorConfig {
    constructor(config: YamlMap) : this (
            config.getFile(c.mpjHome),
            config.getLogLevel(c.logLevel, Level.INFO),
            config.getStringList(c.hosts),
            config.getString(c.portRange) ?: throw IllegalArgumentException("Port range not provided!")
    )
}

/*
    Job queue configuration
 */

interface JobQueueConfig

data class BlockingJobQueueConfig(
        val logLevel: Level = Level.INFO
) : JobQueueConfig {
    constructor(config: YamlMap) : this (
            config.getLogLevel(c.logLevel, Level.INFO)
    )
}

data class MergeQueueConfig(
        val logLevel: Level = Level.INFO
) : JobQueueConfig {
    constructor(config: YamlMap) : this (
            config.getLogLevel(c.logLevel, Level.INFO)
    )
}

/*
    Partition function configuration
 */

interface PartitioningConfig

class UniformPartitionConfig : PartitioningConfig

class SlicePartitionConfig : PartitioningConfig

class HashPartitionConfig : PartitioningConfig

data class BlockPartitionConfig(
        val blockSize : Int = 100
) : PartitioningConfig {
    constructor(config: YamlMap) : this(config.getInt(c.blockSize, 100))
}

/*
    Model configuration
 */

interface ModelConfig

data class ODEModelConfig(
        val file: File,
        val fastApproximation: Boolean,
        val cutToRange: Boolean,
        val selfLoops: Boolean
) : ModelConfig {
    constructor(config: YamlMap) : this(
            config.getFile(c.file) ?: throw IllegalArgumentException("You have to provide an ODE model file!"),
            config.getBoolean(c.fastApproximation, false),
            config.getBoolean(c.cutToRange, false),
            config.getBoolean(c.selfLoops, true)
    )
}

/*
    Checker configuration
 */

data class CheckerConfig(
        val logLevel: Level
) {
    constructor(config: YamlMap) : this(
            config.getLogLevel(c.logLevel, Level.INFO)
    )
}

/*
    Colors configuration
 */

interface ColorsConfig

class RectangularColorsConfig() : ColorsConfig
class SMTColorsConfig(): ColorsConfig