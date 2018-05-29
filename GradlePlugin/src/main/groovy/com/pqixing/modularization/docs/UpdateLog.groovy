package com.pqixing.modularization.docs
//package com.pqixing.modularization.tasks
//
//import com.pqixing.modularization.Default
//import com.pqixing.modularization.utils.FileUtils
//import com.pqixing.modularization.utils.TextUtils
//import org.gradle.api.DefaultTask
//import org.gradle.api.tasks.TaskAction
//
///**
// * Created by pqixing on 17-12-22.
// */
//
//public class com.pqixing.modularization.docs.UpdateLog extends DefaultTask {
//    List<String> modules
//    Map<String, String> envs
//    String compileGroup
//
//    com.pqixing.modularization.docs.UpdateLog(){
//        group = Default.taskGroup
//    }
//    @TaskAction
//    void run() {
//        modules = findModules(project.file("readme"))
//        envs.each { map -> generatorLogFile(map.key, map.value) }
//
//    }
//    /**
//     * 查找当前存在的模块
//     * @return
//     */
//   static List<String> findModules(File dir) {
//        def mds =[]
//        if(dir==null||!dir.exists()) return
//        dir.listFiles(new FilenameFilter() {
//            @Override
//            boolean accept(File file, String s) {
//                return file.isDirectory()
//            }
//        }).each {mds+= it.name}
//        return mds
//    }
//    /**
//     * 生成md文件
//     * @param env
//     * @param urls
//     */
//    void generatorLogFile(String env, String urls) {
//        StringBuilder sb = new StringBuilder("##  大辰组件更新日志   \n").append("仓库地址:　$urls   \n")
//        modules.each { moduleName ->
//            String metaUrl = XmlUtils.getMetaUrl(urls, compileGroup, moduleName)
//            String xmlTxt = XmlUtils.request(metaUrl)
//            if (CheckUtils.isEmpty(xmlTxt)) return
//            sb.append("###   [$moduleName](${metaUrl})    \n")
//                    .append("").append("最新版本:　${XmlUtils.parseXmlByKey(xmlTxt, 'release')}")
//                    .append("　　　　　").append("最后更新时间:　").append(XmlUtils.parseXmlByKey(xmlTxt, "lastUpdated"))
//            File detailFile = new File(project.buildDir, "${env}/${moduleName}.md")
//
//            if (detailFile.exists()) sb.append("　　　　[详细日志](${"../build/${env}/${moduleName}.md"})")
//            sb.append("   \n")
//
//            List<String> versions = XmlUtils.parseListXmlByKey(xmlTxt, "version")
//            sb.append(">   ")
//            def size = versions.size()
//            for (int i = size - 1; i >= Math.max(0, size - 30); i--) {
//                sb.append("[${versions[i]}](${XmlUtils.getPomUrl(urls, compileGroup, moduleName, versions[i])})").append("　　")
//            }
//            sb.append("\n\n >  依赖方式:implementation 'com.dachen.android:router:+'")
//            sb.append("\n  --- \n")
//        }
//
//        FileUtils.write(new File(project.projectDir, "updatelog/${env}.md"), sb.toString())
//    }
//}