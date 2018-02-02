package com.pqixing.modularization.dependent

import com.pqixing.modularization.Keys
import com.pqixing.modularization.base.BaseExtension
import com.pqixing.modularization.configs.BuildConfig
import com.pqixing.modularization.configs.GlobalConfig
import com.pqixing.modularization.maven.MavenType
import com.pqixing.modularization.models.ModuleConfig
import com.pqixing.modularization.utils.CheckUtils
import com.pqixing.modularization.utils.FileUtils
import com.pqixing.modularization.utils.TextUtils
import com.pqixing.modularization.wrapper.MetadataWrapper
import com.pqixing.modularization.wrapper.PomWrapper
import org.gradle.api.Project
/**
 * Created by pqixing on 17-12-25.
 */

class Dependencies extends BaseExtension {

    //对应all*.exclude
    LinkedList<Map<String, String>> allExcludes
    LinkedList<Module> modules
    //传递下来的master分支的exclude
    Set<String> masterExclude = new HashSet<>()
    Set<Module> dependentLose = new HashSet<>()

    boolean hasLocalModule = false
    Set<String> localImportModules

    File versionFile
    Properties versionMaps
    MavenType mavenType
    /**
     * 给全部依赖库添加
     * @param exclude
     */
    void allExclude(Map<String, String> exclude) {
        allExcludes += exclude
    }

    Dependencies(Project project) {
        super(project)
        modules = new LinkedList<>()
        allExcludes = new LinkedList<>()
    }


    Module add(String moduleName, Closure closure = null) {
        def inner = new Module()
        inner.moduleName = moduleName
        if (closure != null) {
            closure.delegate = inner
            closure.setResolveStrategy(Closure.DELEGATE_ONLY)
            closure.call()
        }
        modules += inner
        return inner
    }

    Module addImpl(String moduleName, Closure closure = null) {
        Module inner = add(moduleName, closure)
        inner.scope = "implementation"
        return inner
    }

    List<String> getModuleNames() {
        List<String> names = new LinkedList<>()
        modules.each { names += it.moduleName }
        return names
    }

    /**
     * 获取该aar在仓库的最后一个一个版本
     * @param artifactId
     * @return
     */
    String getLastVersion(String group, String artifactId) {
        String timeStamp = "$artifactId-stamp"
        String version = versionMaps.getProperty(artifactId)
        //一分钟秒内,不更新相同的组件版本,避免不停的爬取相同的接口
        if (System.currentTimeMillis() - (versionMaps.getProperty(timeStamp)?.toLong() ?: 0L) >= 1000 * 60) {
            String release = MetadataWrapper.create(mavenType.maven_url, group, artifactId).release
            if (!CheckUtils.isEmpty(release)) {
                version = release
                versionMaps.put(timeStamp, System.currentTimeMillis().toString())
            }
        }
        return CheckUtils.isEmpty(version) ? "+" : version


    }

    void initVersionMap() {
        mavenType = wrapper.getExtends(ModuleConfig.class).mavenType
        versionFile = new File(BuildConfig.versionDir, "$mavenType.name/$Keys.FILE_VERSION")
        versionMaps = FileUtils.readMaps(versionFile)
        localImportModules = new HashSet<>()
        project.rootProject.allprojects.each { localImportModules += it.name }
    }

    void saveVersionMap() {
        versionMaps?.store(versionFile.newOutputStream(), Keys.CHARSET)
        versionMaps?.clear()
        versionMaps = null
    }
    /**
     * 添加依赖去除
     * @param sb
     * @param module
     */
    String excludeStr(String prefix, List<Map<String, String>> excludes) {
        StringBuilder sb = new StringBuilder()
        excludes.each { item ->
            sb.append("         $prefix (  ")
            item.each { map ->
                sb.append("$map.key : '$map.value',")
            }
            sb.deleteCharAt(sb.length() - 1)
            sb.append("  ) \n")
        }
        return sb.toString()
    }
    /**
     * 进行本地依赖
     * @param module
     * @return
     */
    boolean onLocalCompile(StringBuilder sb, Module module) {
        //如果该依赖没有本地导入，不进行本地依赖
        if (!localImportModules.contains(module.moduleName)) return false
        sb.append("    $module.scope ( project(':$model.moduleName')) \n {")
        sb.append("${excludeStr("exclude", module.excludes)}\n}\n")

        //如果有本地依赖工程，则移除相同的仓库依赖
        hasLocalModule = true
        allExclude(module: module.moduleName)
        allExclude(module: TextUtils.getBranchArtifactId(module.groupId, module.moduleName))
    }
    /**
     * 进行仓库依赖
     * @param module
     * @return
     */
    boolean onMavenCompile(StringBuilder sb, Module module) {
        String lastVersion = getLastVersion(module.groupId, TextUtils.getBranchArtifactId(module.moduleName, wrapper))
        if (!CheckUtils.isVersionCode(lastVersion)) {
            lastVersion = getLastVersion(module.groupId, module.moduleName)
        } else module.artifactId = TextUtils.getBranchArtifactId(module.moduleName, wrapper)

        if (!CheckUtils.isVersionCode(lastVersion)) return false//如果分支和master都没有依赖，则仓库依赖失败

        //如果配置中没有配置指定版本号，用最新版本好，否则，强制使用配置中的版本号
        String focusVersion = ""
        if (!CheckUtils.isVersionCode(module.version)) {//
            focusVersion = " \n force = true \n"
            module.version = lastVersion
        }
        sb.append("    $module.scope  ('$module.groupId:$module.artifactId:$module.version') \n { $focusVersion")
        sb.append("${excludeStr("exclude", module.excludes)}\n}\n")

        //如果依赖的是分支，获取该依赖中传递的master仓库依赖去除
        if (module.artifactId.contains(Keys.BRANCH_TAG)) {
            masterExclude + PomWrapper.create(mavenType.maven_url, module.groupId, module.artifactId).masterExclude
        }
    }
    /**
     * 抛出依赖缺失异常
     * @param module
     */
    void throwCompileLose(Module module) {
        if (GlobalConfig.abortDependenLose) throw new RuntimeException("Lose dependent $module.artifactId , please chack config!!!!!!!")
        dependentLose += module
    }

    @Override
    LinkedList<String> getOutFiles() {
        initVersionMap()

        StringBuilder sb = new StringBuilder("dependencies { \n")
        modules.each { model ->
            if (model.moduleName == project.name) return
            switch (GlobalConfig.dependenModel) {
            //只依赖本地工程
                case "localOnly":
                    if (onLocalCompile(model)) return
                    break
            //优先依赖本地工程
                case "localFirst":
                    if (onLocalCompile(model) || onMavenCompile()) return
                    break
            //优先仓库版本
                case "mavenFirst":
                    if (onMavenCompile() || onLocalCompile(model)) return
                    break
            //只依赖仓库版本
                case "mavenOnly":
                default:
                    if (onMavenCompile()) return
                    break
            }
            throwCompileLose(model)
        }
        sb.append("} \nconfigurations { \\n\"")
        masterExclude.each { name ->
            allExclude(group: GlobalConfig.groupName, module: name)
        }
        allExclude(group: Keys.GROUP_MASTER, module: "${TextUtils.collection2Str(masterExclude)},test")
        sb.append("${excludeStr("all*.exclude", allExcludes)}\n } \n")
        saveVersionMap()
        return [FileUtils.write(new File(project.buildConfig.cacheDir, "dependencies.gradle"), sb.toString())];
    }
}
