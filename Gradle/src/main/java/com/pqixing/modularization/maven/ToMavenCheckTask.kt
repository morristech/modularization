package com.pqixing.modularization.maven

import com.pqixing.git.Components
import com.pqixing.modularization.JGroovyHelper
import com.pqixing.modularization.Keys
import com.pqixing.modularization.android.AndroidPlugin
import com.pqixing.modularization.base.BaseTask
import com.pqixing.modularization.iterface.IExtHelper
import com.pqixing.modularization.manager.ManagerPlugin
import com.pqixing.modularization.manager.ProjectManager

/**
 * 上传到Maven之前检查
 */
open class ToMavenCheckTask : BaseTask() {
    init {
        group = ""
    }

    override fun start() {

    }

    override fun runTask() {
        val extends = ManagerPlugin.getManagerExtends()!!
        val extHelper = JGroovyHelper.getImpl(IExtHelper::class.java)

        val plugin = AndroidPlugin.getPluginByProject(project)
        val lastLog = ProjectManager.findComponent(project.name).lastLog
        val artifactId = if (plugin.APP_TYPE == Components.TYPE_LIBRARY_API && plugin.BUILD_TYPE == Components.TYPE_LIBRARY_API) "${project.name}_api" else project.name
        val groupId = "${extends.groupName}.${lastLog.branch}"

        val baseVersion = extends.baseVersion
        val v = VersionManager.getNewerVersion(lastLog.branch, artifactId, baseVersion)+1
        val version = "$baseVersion.$v"

        val name ="${Keys.PREFIX_LOG}?hash=${lastLog.hash}&commitTime=${lastLog.commitTime}&message=${lastLog.message}"
        extHelper.setMavenInfo(project
                , extends.groupMaven
                , extends.mavenUserName
                , extends.mavenPassWord
                , groupId
                , artifactId
                , version
                , name)

        extHelper.setExtValue(project,Keys.LOG_VERSION,version)
        extHelper.setExtValue(project,Keys.LOG_BRANCH,lastLog.branch)
        extHelper.setExtValue(project,Keys.LOG_MODULE,artifactId)
    }

    override fun end() {
    }
}