package com.pqixing.modularization.maven

import com.pqixing.Tools
import com.pqixing.model.SubModule
import com.pqixing.modularization.JGroovyHelper
import com.pqixing.modularization.Keys
import com.pqixing.modularization.android.AndroidPlugin
import com.pqixing.modularization.android.dps.DpComponents
import com.pqixing.modularization.android.dps.DpsExtends
import com.pqixing.modularization.android.dps.DpsManager
import com.pqixing.modularization.base.BaseTask
import com.pqixing.modularization.iterface.IExtHelper
import com.pqixing.modularization.manager.ManagerPlugin
import com.pqixing.modularization.manager.ProjectManager
import com.pqixing.modularization.utils.GitUtils
import com.pqixing.modularization.utils.ResultUtils
import com.pqixing.tools.TextUtils
import com.pqixing.tools.UrlUtils
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.revwalk.RevCommit
import java.io.File
import java.util.*

/**
 * 上传到Maven之前检查
 */
open class ToMavenCheckTask : BaseTask() {
    init {
        group = ""
    }

    /**
     * toMaven时,忽略检查项目
     * 0:UnCheck null
     * 1:UnCheck branch 不校验新分支第一次提交是否需要升级版本号
     * 2:UnCheck version  不校验是否和上次代码是否相同,允许提交重复
     * 3:UnCheck change  不检验本地是否存在未提交修改
     */
    var unCheck = 0

    override fun start() {
        try {
            unCheck = ManagerPlugin.getExtends().config.toMavenUnCheck.toInt()
        } catch (e: Exception) {
            Tools.println(e.toString())
        }
    }

    override fun runTask() {
        val extends = ManagerPlugin.getExtends()
        val extHelper = JGroovyHelper.getImpl(IExtHelper::class.java)

        val plugin = AndroidPlugin.getPluginByProject(project)
        val dpsExtends = plugin.getExtends(DpsExtends::class.java)
        val subModule = plugin.subModule
//        val lastLog = plugin.subModule
        val artifactId = subModule.name
        if(subModule.getBranch()!=extends.docRepoBranch){
            Tools.println(unCheck - 1, "${subModule.name} branch is ${subModule.getBranch()} , do not match doc branch $extends.docRepoBranch")
            return
        }

        val open = GitUtils.open(File(ProjectManager.codeRootDir, subModule.project.name))
        if (open == null) {
            Tools.printError(-1, "${subModule.project.name} Git open fail, please check")
            return
        }

        checkLocalDps(dpsExtends.compiles)

        checkLoseDps(plugin.dpsManager.loseList)

        val branch = open.repository.branch
        val groupId = "${extends.groupName}.$branch"
        val baseVersion = dpsExtends.toMavenVersion
        checkBaseVersion(baseVersion)

        checkGitStatus(open, subModule)

        val v = VersionManager.getNewerVersion(branch, artifactId, baseVersion)
        val revCommit = loadGitInfo(open, subModule)
        if (revCommit == null) {
            Tools.printError(-1, "${subModule.name} Can not load git info!!")
            return
        }
        checkLastLog(revCommit, artifactId, branch, baseVersion, v)

        val version = "$baseVersion.${v + 1}"

        val name = "${Keys.PREFIX_LOG}?hash=${revCommit.name}&commitTime=${revCommit.commitTime}&message=${revCommit.fullMessage}&desc=${dpsExtends.toMavenDesc}"
        extHelper.setMavenInfo(project
                , extends.groupMaven
                , extends.mavenUserName
                , extends.mavenPassWord
                , groupId
                , artifactId
                , version
                , name)

        extHelper.setExtValue(project, Keys.LOG_VERSION, version)
        extHelper.setExtValue(project, Keys.LOG_BRANCH, branch)
        extHelper.setExtValue(project, Keys.LOG_MODULE, artifactId)
    }

    private fun checkGitStatus(git: Git, subModule: SubModule) {
        if (!GitUtils.checkIfClean(git, getRelativePath(subModule.path))) {
            Tools.println(unCheck - 3, "${subModule.name} Git status is not clean, please check your file!!")
        }
    }

    /**
     * 检查上一个提交版本的日志，如果日志一样，则不允许上传
     */
    private fun checkLastLog(revCommit: RevCommit?, artifactId: String, branch: String, baseVersion: String, v: Int) {
        revCommit ?: return
//        Shell.runSync("git remote update", curLog.gitDir)
//        //获取远程分支最后的版本号
//        val lastRemoteHash = Shell.runSync("git log -1 origin/${curLog.branch} ${if (component.name == component.rootName) "" else component.name}", curLog.gitDir)[0].trim()
//        if (curLog.hash != lastRemoteHash) {
//            Tools.printError("${component.name} Local code is different from remote,Please update your code or Check whether it needs to be push")
//        }

        //检查Maven仓库最后的一个版本的信息
        var lastVersion = v
        var matchBranch = branch
        val match = ManagerPlugin.getExtends().matchingFallbacks
        var i = match.indexOf(matchBranch)
        while (lastVersion < 0 && i < match.size) {
            matchBranch = if (i < 0) branch else match[i]
            lastVersion = VersionManager.getNewerVersion(matchBranch, artifactId, baseVersion)
            i++
        }
        //一条记录都没有，新组件
        if (lastVersion < 0) return

        //如果匹配到的版本不是当前分支，则提示升级版本号
        if (matchBranch != branch) {
            Tools.println(unCheck - 1, "$artifactId Not allow user the same base version on new branch , please update before ToMaven!!!")
        }
        val params = UrlUtils.getParams(DpsManager.getPom(matchBranch, artifactId, "$baseVersion.$lastVersion").name)
        val hash = params["hash"] ?: ""
        val commitTime = params["commitTime"]?.toInt() ?: 0
        if (hash == revCommit.name || revCommit.commitTime < commitTime) {
            //距离上次提交没有变更时,视为成功
            ResultUtils.writeResult("$matchBranch:$artifactId:$baseVersion.$lastVersion The code are not change", 0, unCheck < 2)
        }
    }


    private fun checkBaseVersion(baseVersion: String) {
        if (!TextUtils.isBaseVersion(baseVersion)) Tools.printError(-1, "ToMavenCheckTask $baseVersion is not base version, try x.x etc: 1.0")
    }

    private fun checkLoseDps(loseList: MutableList<String>) {
        if (loseList.isNotEmpty()) {
            Tools.printError(-1, "${project.name}  There are some dependency lose!! -> $loseList")
        }
    }

    private fun checkLocalDps(compiles: HashSet<DpComponents>) {
        val map = compiles.filter { it.localCompile }.map { it.moduleName }
        if (map.isNotEmpty()) {
            Tools.printError(-1, "${project.name} Contain local project, please remove it before upload -> $map")
        }
    }


    override fun end() {
    }

    fun getRelativePath(path: String): String? {
        val of = path.indexOf("/")
        return if (of > 0) return path.substring(of + 1) else null
    }

    fun loadGitInfo(git: Git, subModule: SubModule): RevCommit? {
        val command = git.log().setMaxCount(1)
        getRelativePath(subModule.path)?.apply { command.addPath(this) }
        return command.call().find { true }
    }


}