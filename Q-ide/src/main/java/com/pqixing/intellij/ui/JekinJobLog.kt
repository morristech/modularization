package com.pqixing.intellij.ui

import com.alibaba.fastjson.JSON
import com.dachen.creator.JekinsJob
import com.intellij.openapi.project.Project
import com.pqixing.intellij.utils.UiUtils
import org.apache.commons.httpclient.HttpClient
import org.apache.commons.httpclient.UsernamePasswordCredentials
import org.apache.commons.httpclient.auth.AuthScope
import org.apache.commons.httpclient.methods.PostMethod
import java.net.URL
import java.util.*
import javax.swing.*

class JekinJobLog(val project: Project, val jobUrl: String) : BaseJDialog() {
    private lateinit var contentPane: JPanel
    private lateinit var tpLog: JTextPane
    private lateinit var stopButton: JButton
    private lateinit var jlTitle: JLabel
    private lateinit var jpScroll: JScrollPane
    private lateinit var cbAll: JCheckBox

    init {
        setContentPane(contentPane)
        isModal = false
        title = "Console"
        stopButton.addActionListener {
            val client = HttpClient()
            client.state.setCredentials(AuthScope("https://192.168.3.7:8080/jenkins", 443, "realm"),
                    UsernamePasswordCredentials("pengqixing", "8aaf320380121c22d276648cc2a5cef6"))
            client.params.isAuthenticationPreemptive = true

            val post = PostMethod("${jobUrl}stop".replace("http://", "https://"))
            post.doAuthentication = true
            client.executeMethod(post)
            post.responseBodyAsString
        }
        cbAll.addActionListener { reloadJob() }
        reloadJob()
    }

    fun reloadJob() = Thread(Runnable {
        var job: JekinsJob? = null
        do {
            try {
                job = JSON.parseObject(URL(jobUrl + "api/json").readText(), JekinsJob::class.java)
//                stopButton.isVisible = job.building
                stopButton.isVisible = false
                jlTitle.text = "${job.displayName} -> ${Date(job.timestamp).toLocaleString()} -> ${getDuration(job)} -> ${if (job.building) "BUILDING" else job.result}"
                tpLog.text = URL(jobUrl + "consoleText").readText().lines().reversed().joinToString(separator = "\n", limit = if (cbAll.isSelected) -1 else 40)
//                val scrollBar = jpScroll.verticalScrollBar
//                scrollBar.value = scrollBar.maximum
            } catch (e: Exception) {
            }
            Thread.sleep(3000)
        } while (job?.building == true)
    }).start()

    private fun getDuration(job: JekinsJob?): String {
        job ?: return ""
        var timemills = (if (job.building) (System.currentTimeMillis() - job.timestamp) else job.duration) / 1000
        return "${timemills / 60}分 ${timemills % 60}秒"
    }
}
