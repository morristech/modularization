package com.pqixing.modularization.maven

import com.pqixing.modularization.base.BaseTask
import com.pqixing.modularization.common.GlobalConfig
import com.pqixing.modularization.net.Net
import com.pqixing.modularization.utils.MavenUtils
import com.pqixing.modularization.utils.TextUtils

class UpdateMavenTask extends BaseTask {
    @Override
    void start() {
        String groupUrl = TextUtils.getUrl(GlobalConfig.groupName)
        int length = groupUrl.length()
        GlobalConfig.preMavenUrl.each { mavenInfo ->
            Net.get("$mavenInfo.value/$groupUrl").eachLine { line ->
                int groupIndex = line.indexOf(groupUrl)
                if (groupIndex != -1 && line.contains("href=")) {
                    String artifactId = line.substring(groupIndex + length + 1, line.indexOf("/\">"))
                    MavenUtils.updateMavenRecord(wrapper, mavenInfo.key, mavenInfo.value, artifactId)
                }
            }
        }
    }

    @Override
    void runTask() {
    }

    @Override
    void end() {
    }

}