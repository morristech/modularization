//package com.pqixing.modularization.android.tasks
//
//import com.pqixing.modularization.Keys
//import com.pqixing.modularization.base.BaseTask
//import com.pqixing.modularization.modularization.FileUtils
//import com.pqixing.modularization.modularization.Print
//import com.pqixing.tools.TextUtils
//
//class BuildFirstTask extends BaseTask {
//    String type = "Debug"
//    String flavorName = ""
//
//    String appName
//
//    void setBuildType(String buildType) {
//        type = buildType
//        project.android.productFlavors?.each {
//            if (flavorName.isEmpty()) flavorName = it.name
//            if ("preTest" == it.name) flavorName = it.name
//        }
//        appName = TextUtils.getSystemEnv(Keys.ENV_BUILD_APP_NAME) ?: project.name
//        this.dependsOn "assemble${TextUtils.firstUp(flavorName)}$type"
//    }
//
//    @Override
//    void start() {
//
//    }
//
//    @Override
//    void runTask() {
//        def path = FileUtils.urls("outputs", "apk", flavorName, type.toLowerCase())
//        def name = TextUtils.append("-", project.name, flavorName, type.toLowerCase()) + ".apk"
//        def buildFile = new File(project.buildDir, "$path/$name")
//        //重命名文件
//        if (buildFile.exists()) {
//            def temp = buildFile
//            buildFile = new File(project.rootProject.buildDir, "$appName-${type}.apk")
//            temp.renameTo(buildFile)
//        }
//        Print.lnIde("buildApk=${buildFile.absolutePath}")
//    }
//
//    @Override
//    void end() {
//
//    }
//}
