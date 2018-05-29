package com.pqixing.modularization.wrapper

import auto.Android
import groovy.xml.Namespace
/**
 * 拓展类Utils
 */
class ManifestWrapper extends XmlWrapper {
    Namespace android
    Namespace tools
    Node appNode

    public ManifestWrapper(String xmlString) {
        super(xmlString)
        def appNodes = node.application
        if (appNodes.isEmpty()) {
            appNode = new XmlParser().parseText(new Android([:]).nodeApp)
//            appNode = new Node()
            node.append(appNode)
        } else appNode = appNodes[0]

        // 声明命名空间
        android = new Namespace('http://schemas.android.com/apk/res/android', 'android')
        tools = new Namespace('http://schemas.android.com/tools', 'tools')
    }
    /**
     * 添加四大组件
     * @param node
     * @return
     */
    ManifestWrapper addComponent(Node node) {
        appNode.append(node)
        return this
    }
    /**
     * 添加四大组件
     * @param node
     * @return
     */
    void setApplication(String applicationName) {
        appNode.attributes().put(android.name, applicationName)
    }
    /**
     * 添加四大组件
     * @param node
     * @return
     */
    void setAppTheme(String theme) {
        appNode.attributes().put(android.theme, theme)
    }
    /**
     * 添加四大组件
     * @param node
     * @return
     */
    void setAppLabel(String label) {
        appNode.attributes().put(android.label, label)
    }
    /**
     * 根据当前属性值，更新replace属性
     */
    ManifestWrapper updateReplace() {
        StringBuilder sb = new StringBuilder()
        appNode.attributes().each { item ->
            String key = item.key.toString().replaceFirst("\\{.*}", "")
            if (key != "replace") sb.append("android:$key,")
        }
        appNode.attributes().put(tools.replace, sb.substring(0, sb.length() - 1))
        return this
    }
}