package com.pqixing.modularization.git

import com.pqixing.modularization.base.BaseTask
import com.pqixing.modularization.utils.CheckUtils
import com.pqixing.modularization.utils.GitUtils
import com.pqixing.modularization.utils.Print

/**
 * Created by pqixing on 17-12-20.
 * 同步文档的任务
 */

abstract class GitTask extends BaseTask {
    GitConfig gitConfig
    /**
     * 待操作的目标git目录
     */
    Set<GitProject> targetGits
    String target = "include"

    @Override
    void start() {
        gitConfig = wrapper.getExtends(GitConfig)
        target = gitConfig.target
        targetGits = new HashSet<>()
    }

    @Override
    void runTask() {
        if (gitConfig.target == "all") {
            targetGits.addAll(GitConfig.allGitProjects)
        } else {
            Set<String> gitDirNames = new HashSet<>()
            project.rootProject.allprojects.each { p ->
                String gitName = GitUtils.findGitDir(p.projectDir)?.name
                if (!CheckUtils.isEmpty(gitName)) gitDirNames.add(gitName)
            }
            GitConfig.allGitProjects.each { p ->
                if (gitDirNames.contains(p.name)) targetGits.add(p)
            }
        }
        targetGits.each { p ->
            Print.ln("GitTask onGitProject $p.name $p.gitUrl")
            onGitProject(p.name, p.gitUrl, new File(project.rootDir.parentFile, p.name))
        }
    }
    /**
     * 执行git命令的方法
     * @param gitName
     * @param gitUrl
     * @param gitDir
     */
    abstract String onGitProject(String gitName, String gitUrl, File gitDir)

    @Override
    void end() {

    }
}
