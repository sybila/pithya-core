package com.github.sybila.biodivine.ctl


/*
data class PropertyConfig(
        val formula: String? = null,
        val verify: String? = null,
        val file: File? = null,
        val results: Set<String> = setOf()
) {
    constructor(config: YamlMap) : this(
            config.getString(c.formula),
            config.getString(c.verify),
            config.getFile(c.file),
            config.getStringList(c.results).toSet()
    )
}

fun YamlMap.loadPropertyList(): List<PropertyConfig> {
    return this.getMapList(c.properties).map { PropertyConfig(it) }
}*/