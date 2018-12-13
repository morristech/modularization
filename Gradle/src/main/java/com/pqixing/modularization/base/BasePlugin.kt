package com.pqixing.modularization.base


import com.alibaba.fastjson.JSON
import com.pqixing.ProjectInfo
import com.pqixing.Tools
import com.pqixing.interfaces.ICredential
import com.pqixing.interfaces.ILog
import com.pqixing.modularization.FileNames
import com.pqixing.modularization.JGroovyHelper
import com.pqixing.modularization.Keys
import com.pqixing.modularization.iterface.IExtHelper
import com.pqixing.modularization.manager.FileManager
import com.pqixing.tools.CheckUtils
import com.pqixing.tools.FileUtils
import com.pqixing.tools.TextUtils
import groovy.lang.GroovyClassLoader
import org.gradle.BuildAdapter
import org.gradle.BuildListener
import org.gradle.BuildResult
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.invocation.Gradle
import java.io.File
import java.util.*

/**
 * Created by pqixing on 17-12-20.
 */

abstract class BasePlugin : Plugin<Project>, IPlugin {
    lateinit var p: Project
    var isEmptyTask:Boolean = false
    val extHelper = JGroovyHelper.getImpl(IExtHelper::class.java)

    protected abstract val applyFiles: List<String>

    private val tasks = HashMap<String, Task>()

    override var projectInfo: ProjectInfo = ProjectInfo()
        get() {
            if (pi == null) {
                var infoStr = jsonFromEnv
                if (CheckUtils.isEmpty(infoStr)) {
                    try {
                        val parseClass = GroovyClassLoader().parseClass(File(rootDir, FileNames.PROJECT_INFO))
                        infoStr = JSON.toJSONString(parseClass.newInstance())
                    } catch (e: Exception) {

                    }

                }
                if (!CheckUtils.isEmpty(infoStr)) {
                    try {
                        pi = JSON.parseObject(infoStr, ProjectInfo::class.java)
                    } catch (e: Exception) {
                    }

                }
                if (pi == null) pi = ProjectInfo()
            }
            return pi!!
        }

    private val jsonFromEnv: String
        get() {
            val infoStr = TextUtils.getSystemEnv("ProjectInfo") ?: return ""
            return String(Base64.getDecoder().decode(infoStr.toByteArray()))
        }

    override val project: Project
        get() = p
    override val rootDir: File
        get() = project.rootDir

    override val projectDir: File
        get() = project.projectDir

    override val buildDir: File
        get() = project.buildDir

    override val cacheDir: File
        get() = File(buildDir, FileNames.MODULARIZATION)

    override fun apply(project: Project) {
        initProject(project)
    }

    override fun getGradle(): Gradle = p.gradle

    fun setPlugin() {
        pluginCache[javaClass.simpleName] = this
    }


    protected fun initProject(project: Project) {
        isEmptyTask = project.gradle.startParameter.taskNames.isEmpty()
        this.p = project
        setPlugin()
        initTools(project)
        createIgnoreFile()

        val file = File(FileManager.docRoot, "gradles")
        extHelper.setExtValue(project, "gradles", file.absolutePath)

        callBeforeApplyMould()
        applyFiles.forEach {
            val f = File(file, "$it.gradle")
            if (f.exists() && f.isFile)
                project.apply(mapOf<String,String>("from" to f.absolutePath))
        }
        linkTask()?.forEach { onTaskCreate(it, BaseTask.task(project, it)) }
    }

    protected fun onTaskCreate(taskClass: Class<*>, task: Task) {

    }

    override fun <T> getExtends(tClass: Class<T>): T {
        return project.extensions.getByType(tClass)
    }

    override fun getTask(taskClass: Class<out Task>): Set<Task> {
        return project.getTasksByName(BaseTask.getTaskName(taskClass), false)
    }

    fun createIgnoreFile() {
        val ignoreFile = project.file(FileNames.GIT_IGNORE)
        val defSets = mutableSetOf<String>("build"
                , Keys.FOCUS_GRADLE
                , FileNames.MODULARIZATION
                , "*.iml", "import.kt")
        defSets += ignoreFields
        val old = FileUtils.readText(ignoreFile) ?: ""
        old.lines().forEach { line -> defSets.remove(line.trim()) }

        if (defSets.isEmpty()) return
        val txt = StringBuilder(old)
        defSets.forEach { txt.append("\n$it") }
        FileUtils.writeText(ignoreFile, txt.toString())
    }

    private fun initTools(project: Project) {
        if (!Tools.init) {
            Tools.init(object : ILog {
                override fun printError(l: String?) = throw  GradleException(l)

                override fun println(l: String?) = System.out.println(l)
            }, project.rootDir.absolutePath, object : ICredential {
                override fun getUserName() = projectInfo?.gitUserName ?: ""

                override fun getPassWord() = projectInfo?.gitPassWord ?: ""
            })
        }
    }

    companion object {
        private val pluginCache = HashMap<String, IPlugin>()

        fun <T : IPlugin> getPlugin(pluginClass: Class<T>): T? {
            return pluginCache[pluginClass.simpleName] as T
        }

        var pi: ProjectInfo? = null
    }
}
