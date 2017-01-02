package com.github.sybila.biodivine

/*
fun File.toYamlMap(): YamlMap {
    return YamlMap(Yaml().load(this.inputStream()) as Map<*,*>)
}

fun String.toYamlMap(): YamlMap {
    return YamlMap(Yaml().load(this) as Map<*, *>)
}

class YamlMap internal constructor(private val data: Map<*,*>) {

    fun getString(key: String, default: String): String {
        return data[key] as String? ?: default
    }

    fun getAny(key: String): Any? {
        return data[key]
    }

    fun getString(key: String): String? {
        return data[key] as String?
    }

    fun getInt(key: String, default: Int): Int {
        return data[key] as Int? ?: default
    }

    fun getBoolean(key: String, default: Boolean): Boolean {
        return data[key] as Boolean? ?: default
    }

    fun getMap(key: String): YamlMap {
        return YamlMap(data[key] as Map<*,*>? ?: mapOf<Any,Any>())
    }

    fun getFile(key: String): File? {
        return data[key]?.run { File(this as String) }
    }

    fun getMapList(key: String): List<YamlMap> {
        val list = data[key] as List<*>? ?: listOf<Any>()
        return list.map { YamlMap(it as Map<*, *>) }
    }

    fun getStringList(key: String): List<String> {
        val list = data[key] as List<*>? ?: listOf<Any>()
        return list.map { it.toString() }
    }

    fun getLogLevel(key: String, default: Level): Level {
        val level = data[key] as String?
        return level?.toLogLevel() ?: default
    }

    override fun toString(): String {
        return Yaml().dump(this.data)
    }
}*/