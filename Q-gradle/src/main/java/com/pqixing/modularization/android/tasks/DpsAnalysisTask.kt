package com.pqixing.modularization.android.tasks

import com.pqixing.Tools
import com.pqixing.help.XmlHelper
import com.pqixing.model.SubModuleType
import com.pqixing.modularization.JGroovyHelper
import com.pqixing.modularization.Keys
import com.pqixing.modularization.android.AndroidPlugin
import com.pqixing.modularization.android.dps.DpsExtends
import com.pqixing.modularization.android.dps.DpsManager
import com.pqixing.modularization.base.BaseTask
import com.pqixing.modularization.iterface.IExtHelper
import com.pqixing.modularization.manager.FileManager
import com.pqixing.modularization.manager.ManagerPlugin
import com.pqixing.modularization.manager.ProjectManager
import com.pqixing.modularization.maven.VersionManager
import com.pqixing.modularization.utils.ResultUtils
import com.pqixing.tools.FileUtils
import com.pqixing.tools.TextUtils
import com.pqixing.tools.UrlUtils
import java.io.File
import java.util.*
import kotlin.collections.HashMap
import kotlin.collections.HashSet

/**
 * 依赖对比分析
 * 1,生成模块的依赖分析文件
 * 2，
 */
open class DpsAnalysisTask : BaseTask() {
    val plugin = AndroidPlugin.getPluginByProject(project)
    val groupName = ManagerPlugin.getExtends().groupName
    val dir = File(plugin.cacheDir, "report")
    val temp = File(dir, "DependencyReport.txt")
    //    val temp = File(dir, "DpsReport.bak")
    val versions = TreeMap<String, String>()


    val compareFile = File(dir, Keys.TXT_DPS_COMPARE)

    init {
        val dpPrint = project.tasks.create("DependencyReport", org.gradle.api.tasks.diagnostics.DependencyReportTask::class.java)
        dpPrint.outputFile = temp
        this.dependsOn(dpPrint)
    }


    //依赖分析第一步
    val allDps = LinkedList<Vertex>()
    val extHelper = JGroovyHelper.getImpl(IExtHelper::class.java)
    val dependentModel: String = ManagerPlugin.getExtends().config.dependentModel

    //生成DpsReport.txt
    override fun start() {
        if (!temp.exists()) {
            Tools.println("Can not find ${temp.absolutePath}")
            return
        }
        val result = TreeMap<String, String>()
        var read = 0
//        reportFile.forEachLine { it ->
        temp.forEachLine { it ->
            //如果包含 releaseCompileClasspath,则下一行开始解析 note: r可能为大写，所以无需匹配
            if (it.contains("eleaseCompileClasspath")) {
                read++
                return@forEachLine
            }
            if (read != 1) return@forEachLine
            val line = trimUnUse(it)
            if (line.isEmpty()) {
                read++
                return@forEachLine
            }
            val ls = line.split(":")
            if (ls.size < 2) return@forEachLine
            if (ls.size == 2) {//本地project类型依赖
                result[ls[1]] = ls[0]
                return@forEachLine
            }
            val key = "${ls[0]}:${ls[1]}"

            //查出已经保存的版本号
            val oldVersion = result[key]

            //版本号是否是提升
            val upgradle = ls[2].indexOf("->")
            val version = if (upgradle < 0) ls[2].trim() else ls[2].substring(upgradle).trim()

            result[key] = maxVersion(oldVersion, version)
        }
        val resultStr = StringBuilder()
        result["CreateTime"] = Date().toLocaleString()
        result.forEach { k, v ->
            val version = v.split("->").last().trim()
            resultStr.append("$k=$version\n")
            versions[k] = version
        }
        FileUtils.writeText(File(dir, Keys.TXT_DPS_REPORT), resultStr.toString())
    }

    /**
     * 比较版本号
     */
    private fun maxVersion(oldVersion: String?, version: String): String {
        oldVersion ?: return version
        val old = oldVersion.split("->")
        val v = version.split("->")
        if (old.size > v.size) return oldVersion
        if (old.size < v.size) return version
        return if (TextUtils.compareVersion(old.last(), v.last()) > 0) oldVersion else version
    }

    /**
     * 解析出需要分析的文字
     */
    private fun trimUnUse(it: String): String {
        return it.split("---").last().replace(Regex("(\\(.*?\\))|,| FAILED"), "").trim()
    }

    //DpsCompare.txt
    override fun end() {
        val oldReport = File(project.projectDir, "DpsReport.txt")
        if (!oldReport.exists()) {
            Tools.println("Compare dps fail,please put the old report file on project dir and try again!!${oldReport.absolutePath}")
            ResultUtils.writeResult(File(dir, Keys.TXT_DPS_REPORT).absolutePath)
            return
        }
        //加载旧的版本
        val oldVersions = oldReport.readLines().filter { it.contains("=") }.map {
            val vs = it.split("=")
            vs[0].trim() to vs[1].trim()
        }.toMap(HashMap())

//        if (true) {
//            Tools.println("end -> $oldVersions")
//            Tools.println("end -> $versions")
//            return
//        }
        val innerModules = allDps.map { it.name }.toSet()
        val innerList = LinkedList<String>()
        val thirdList = LinkedList<String>()

        val result = StringBuilder("Compare Dependencies Version : ${oldVersions.remove("CreateTime")} -> ${versions.remove("CreateTime")}\n")
        versions.forEach { k, n ->

            val o = oldVersions.remove(k)
//            Tools.println("versions.forEach -> $k ->$o -> $n")
            val t = if (o == null) "add  " else if (o == n) "equal" else "diff "

            val inner = k.startsWith(groupName) || innerModules.contains(k)

            val l = "  |-- $t   ${removeGroup(k, inner)} : ${appendVersion(n, o)}  ${getDescFromPom(k, o, n, inner)} \n"
            (if (inner) innerList else thirdList).add(l)
        }

        oldVersions.forEach { k, o ->
            val t = "del  "
            val inner = k.startsWith(groupName) || innerModules.contains(k)

            val l = "  |-- $t   ${removeGroup(k, inner)} : $o  ${getDescFromPom(k, o, null, inner)} \n"
            (if (inner) innerList else thirdList).add(l)
        }

        result.append("Inner -> \n")
        innerList.sortedBy { it }.forEach { result.append(it) }

        result.append("\nThird -> \n")
        thirdList.sortedBy { it }.forEach { result.append(it) }
        val rl = result.toString()
        if(!ResultUtils.ide) Tools.println(rl)
        FileUtils.writeText(compareFile, rl)

        clear()
        ResultUtils.writeResult(compareFile.absolutePath, 0)
    }

    private fun clear() {
        versions.clear()
        allDps.clear()
    }

    private fun removeGroup(key: String, inner: Boolean): String {
        if (!inner) return key
        val name = key.replace(groupName, "")
        return if (name.startsWith(".")) name.substring(1) else name
    }

    private fun appendVersion(n: String, o: String?): String {
        o ?: return n
        if (n == o) return n
        return "$o -> $n"
    }

    /**
     * 从pom文件获取组件的更新说明
     */
    private fun getDescFromPom(k: String, o: String?, n: String?, inner: Boolean): String {
        if (!inner) return ""
        val version = if (TextUtils.isVersionCode(n)) n else o
        if (!TextUtils.isVersionCode(version)) {
            Tools.println("$k getDescFromPom Exception -> old version : $o , new version $n")
            return ""
        }
        val t = k.split(":")
        val branch = removeGroup(t[0], true)
        if (branch.isEmpty()) return ""
        val module = t[1]
        val params = UrlUtils.getParams(DpsManager.getPom(branch, module, version!!).name)
        val commitTime = params["commitTime"]?.toInt() ?: 0
        params["commitTime"] = Date(commitTime * 1000L).toLocaleString()
        return "    ====> log : " + getCollectionStr(params).replace("\n", " ")
    }

    //生成 DpsAnalysis.txt
    override fun runTask() {
        val dpsExt = plugin.dpsManager.dpsExt
        //依赖排序起点
        val topVertex = Vertex(project.name)
        allDps.add(topVertex)

        val findApi = plugin.subModule.findApi()
        //如果是Api类型，先添加api模块依赖
        if (findApi != null) {
            topVertex.dps.add(findApi.name)
        }
        dpsExt.compiles.forEach { topVertex.dps.add(it.moduleName) }
        val branch = plugin.subModule.getBranch()

        //加载定点依赖的全部依赖
        topVertex.dps.forEach { loadDps(it, branch, dpsExt) }
        topoSort(allDps.first)
        val resultStr = StringBuilder("#" + Date().toLocaleString()).append("\n")

        val include = LinkedList<String>()
        val toMavens = LinkedList<String>()
        for (i in (allDps.size - 1) downTo 0) {
            val d = allDps[i]
            val moduleName = d.name
            if (!include.contains(moduleName)) include.addFirst(moduleName)
            val taskName = ":$moduleName:ToMaven"
            if (!toMavens.contains(taskName)) {
                toMavens.add("./gradlew $taskName -Dinclude=Auto -DdependentModel=mavenOnly -D-DbuildDir=maven \n")
            }
        }
        resultStr.append("#SortByDegree=${getCollectionStr(allDps)} \n")
        resultStr.append("\ncurPath=$(pwd) \n\n")
        resultStr.append("echo Start All ToMaven Task!! \n")
        resultStr.append("cd ${project.rootDir.absolutePath} \n")
        toMavens.forEach { resultStr.append(it) }
        resultStr.append("echo End All ToMaven Task!!\n")
        resultStr.append("cd \$curPath \n")
        FileUtils.writeText(File(dir, Keys.TXT_DPS_ANALYSIS), resultStr.toString())
        FileUtils.writeText(File(plugin.rootDir, "build/dps/${project.name}.dp"), getCollectionStr(include))
    }

    private fun getCollectionStr(include: Any): String {
        val toString = include.toString()
        return toString.substring(1, toString.length - 1)
    }

    /**
     * 拓扑排序 将依赖管理有序排序
     */
    private fun topoSort(first: Vertex) {
        val queue = LinkedList<Vertex>()
        queue.offer(first)
        val circles = HashSet<String>()
        outer@ while (queue.isNotEmpty()) {
            //后进后出，深度优先查询
            val top = queue.last
            for (d in top.dps) {
                val v = allDps.find { f -> f.name == d } ?: continue
                //如果该依赖不在需要处理的队列时，把依赖加入待处理队列
                if (queue.contains(v).apply { if (this) circles.add("${top.name}<->${v.name}") }
                        || v.degree > top.degree) continue
                //如果该依赖层级增加，则，该依赖的所有依赖层级都需要+1
                v.degree = top.degree + 1
                queue.addLast(v)
                continue@outer//直接轮循下一个
            }
            //循环完毕，移除
            queue.removeLast()
        }
        if (circles.isNotEmpty()) {
            Tools.println("Has circle dependency -> $circles")
        }
        allDps.sortBy { it.degree }
    }

    fun loadDps(module: String, branch: String, dpsExt: DpsExtends) {

        //如果已经处理过该模块的依赖，不重复处理
        if (allDps.any { it.name == module } || ProjectManager.findSubModuleByName(module) == null) return

        val tempContainer = Vertex(module).apply { allDps.add(this) }.dps
        val compile = when (dependentModel) {
            "localOnly" -> loadDpsFromLocal(module, dpsExt, tempContainer)
            "localFirst" -> loadDpsFromLocal(module, dpsExt, tempContainer) || loadDpsFromMaven(module, branch, tempContainer)
            "mavenFirst" -> loadDpsFromMaven(module, branch, tempContainer) || loadDpsFromLocal(module, dpsExt, tempContainer)
            else -> loadDpsFromMaven(module, branch, tempContainer)
        }

        //依赖解析失败，报错
        if (!compile) Tools.printError(-1, "DpsAnalysisTask Exception-> can not resolve dps for $module , mode :$dependentModel")
        tempContainer.forEach { loadDps(it, branch, dpsExt) }
    }

    private fun checkModule(module: String) = module.startsWith("$groupName.")

    /**
     * 从本地获取依赖
     */
    fun loadDpsFromLocal(module: String, dpsExt: DpsExtends, dpsContainer: HashSet<String>): Boolean {

        val mcp = ProjectManager.findSubModuleByName(module) ?: return false


        //查出buildGradle
        val buildGradle = File(ProjectManager.codeRootDir, "${mcp.path}/build.gradle")
        val libraryGradle = File(FileManager.templetRoot, "gradles/com.module.library.gradle")
        //文件不存，则解析失败
        if (!buildGradle.exists()) return false

        //先清楚旧的依赖数据，然后重新加载
        dpsExt.compiles.clear()
        extHelper.setExtValue(project, "moduleName", mcp.name)
        try {
            plugin.isApp = mcp.type == SubModuleType.TYPE_APPLICATION
            plugin.buildAsApp = plugin.isApp
            project.apply(mapOf<String, String>("from" to buildGradle.absolutePath))
        } catch (e: Exception) {
        }
        try {
            project.apply(mapOf<String, String>("from" to libraryGradle.absolutePath))
        } catch (e: Exception) {
        }
        dpsExt.compiles.forEach { dpsContainer.add(it.moduleName) }
        //解析build.gradle文件，加载本地配置的依赖数据
        Tools.println("loadDpsFromLocal $module dps -> $dpsContainer")
        return true
    }

    /**
     * 从本地获取依赖
     */
    fun loadDpsFromMaven(module: String, branch: String, dpsContainer: HashSet<String>): Boolean {
        val version = VersionManager.getVersion(branch, module, "+")
        if (version.first.isEmpty()) return false
        DpsManager.getPom(version.first, module, version.second).dependency.map {
            val p = XmlHelper.strToPair(it)
            if (p.second != null) dpsContainer.add(p.second!!)
        }
        //解析build.gradle文件，加载本地配置的依赖数据
        Tools.println("loadDpsFromMaven $module dps -> $dpsContainer")
        return true
    }

}

/**
 * 依赖处理的临时类，用于进行依赖排序
 */
class Vertex(var name: String, var degree: Int = 0, var dps: HashSet<String> = HashSet()) {
    override fun toString(): String = "$name:$degree"
}