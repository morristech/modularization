package com.pqixing.intellij.actions

import com.pqixing.intellij.adapter.JListInfo
import com.pqixing.intellij.ui.GitOperatorDialog
import git4idea.GitUtil
import java.io.File

class GitSyncAction : BaseGitAction() {

    override fun checkUrls(urls: Map<String, String>): Boolean=true

    override fun getAdapterList(urls: Map<String, String>): MutableList<JListInfo> {
        val allDatas = urls.map {
            JListInfo(it.key, if ( GitUtil.isGitRoot(File(it.key))) "" else "No Exists", select = true)
        }.sortedBy { it.log }.toMutableList()
        allDatas.add(0, JListInfo("$basePath/templet", select = true))
        return allDatas
    }

    override fun checkOnOk(allDatas: MutableList<JListInfo>, dialog: GitOperatorDialog): Boolean = true

    override fun initDialog(dialog: GitOperatorDialog) {
        dialog.setOperator("clone", "update", "push")
        dialog.setTargetBranch(null, false)
    }
}