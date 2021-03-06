package com.pqixing.modularization.base


import com.pqixing.Config
import com.pqixing.modularization.FileNames
import com.pqixing.modularization.JGroovyHelper
import com.pqixing.modularization.interfaces.OnClear
import com.pqixing.modularization.iterface.IExtHelper
import com.pqixing.modularization.manager.FileManager
import com.pqixing.modularization.manager.ManagerPlugin
import com.pqixing.tools.FileUtils
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.invocation.Gradle
import java.io.File

/**
 * Created by pqixing on 17-12-20.
 */

abstract class BasePlugin : Plugin<Project>, IPlugin {
    lateinit var p: Project
    val extHelper = JGroovyHelper.getImpl(IExtHelper::class.java)

    protected abstract val applyFiles: List<String>

    override val config: Config
        get() = ManagerPlugin.getExtends().config

    override val project: Project
        get() = p
    override val rootDir: File
        get() = project.rootDir

    override val projectDir: File
        get() = project.projectDir

    override val buildDir: File
        get() = project.buildDir

    override val cacheDir: File
        get() {
            val suffix = if (project == project.rootProject) "" else "_${buildDir.name}"
            return File(projectDir, "build/${FileNames.MODULARIZATION}$suffix")
        }

    override fun apply(project: Project) {
        initProject(project)
    }

    override fun getGradle(): Gradle = p.gradle


    protected fun initProject(project: Project) {
        this.p = project
        createIgnoreFile()

        val file = File(FileManager.templetRoot, "gradles")
        extHelper.setExtValue(project, "gradles", file.absolutePath)

        callBeforeApplyMould()
        applyFiles.forEach {
            val f = File(file, "$it.gradle")
            if (f.exists() && f.isFile)
                project.apply(mapOf<String, String>("from" to f.absolutePath))
        }
        linkTask().forEach { onTaskCreate(it, BaseTask.task(project, it)) }
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

        val defSets = mutableSetOf("build", "*.iml")
        defSets += ignoreFields
        val old = FileUtils.readText(ignoreFile) ?: ""
        old.lines().forEach { line -> defSets.remove(trimIgnoreKey(line.trim())) }

        if (defSets.isEmpty()) return
        val txt = StringBuilder(old)
        defSets.forEach { txt.append("\n$it") }
        FileUtils.writeText(ignoreFile, txt.toString())
    }

    fun trimIgnoreKey(key: String): String {
        var start = 0
        var end = key.length
        if (key.startsWith("/")) start++
        if (key.endsWith("/")) end--
        return key.substring(start, end)
    }



    companion object {
        val listeners = mutableSetOf<OnClear>()
        fun addClearLister(l: OnClear) {
            listeners.add(l)
        }

        fun onClear() {
            listeners.forEach { it.clear() }
        }
        fun onStart(){
            listeners.forEach { it.start() }
        }
    }
}
