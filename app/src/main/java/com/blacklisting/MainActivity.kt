package com.blacklisting

import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.SpinnerAdapter
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.widget.TextViewCompat
import com.alibaba.fastjson2.JSONArray
import com.blacklisting.databinding.ActivityMainBinding
import com.blacklisting.ds.github.Org
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.ProgressMonitor
import java.io.File
import java.net.URL
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity()
{
    private lateinit var binding: ActivityMainBinding

    private lateinit var notificationManager: NotificationManager
    private lateinit var gitNotificationChannel: NotificationChannel

    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.spinContent.adapter = object : BaseAdapter() {
            val list = listEntries()?: emptyList()
            override fun getCount(): Int = list.size

            override fun getItem(position: Int): Any = list[position]

            override fun getItemId(position: Int): Long = position.toLong()

            override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View
            {
                return TextView(this@MainActivity).apply {
                    text = list[position].second
                }
            }
        }

        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        gitNotificationChannel = NotificationChannel("Git", "Git Action", NotificationManager.IMPORTANCE_DEFAULT)
        notificationManager.createNotificationChannel(gitNotificationChannel)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean
    {
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean
    {
        return when (item.itemId)
        {
            R.id.menu_main_init_or_update ->
                {
                    initOrPullRepos()
                    true
                }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun initOrPullRepos()
    {
        thread {
            val repos = with (URL("https://api.github.com/orgs/blacklisting/repos").openConnection().getInputStream()) {
                bufferedReader().readText().apply {
                    close()
                }
            }
            JSONArray.parseArray(repos, Org::class.java).filter {
                it.name !in listOf("android-helper")
            }.apply {
                Log.i(GIT_TAG, "Found repos ${joinToString(transform = Org::name)}")
            }.forEach {
                File("${filesDir.absolutePath}/${it.name}").apply {
                    if (!exists())
                    {
                        Log.i(GIT_TAG, "Initializing ${it.name}")
                        notificationManager.notify(
                            it.name.hashCode(),
                            NotificationCompat.Builder(this@MainActivity, "Git")
                                .setContentTitle("${it.name} start initializing.")
                                .setContentText("")
                                .setSmallIcon(R.drawable.ic_launcher_foreground)
                                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                                .setAutoCancel(true)
                                .build()
                        )
                        try
                        {
                            Git.cloneRepository()
                                .setDirectory(this)
                                .setURI(it.cloneUrl)
                                .setProgressMonitor(object : ProgressMonitor
                                {
                                    var totalWork = 0
                                    override fun start(totalTasks: Int)
                                    {
                                        Log.i(GIT_TAG, "Start $totalTasks")
                                        notificationManager.notify(
                                            it.name.hashCode(),
                                            NotificationCompat.Builder(this@MainActivity, "Git")
                                                .setContentTitle("${it.name} start initializing.")
                                                .setContentText("0 / $totalTasks")
                                                .setSmallIcon(R.drawable.ic_launcher_foreground)
                                                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                                                .setAutoCancel(true)
                                                .build()
                                        )
                                    }

                                    override fun beginTask(title: String?, totalWork: Int)
                                    {
                                        Log.i(GIT_TAG, "Begin $title $totalWork")
                                        this.totalWork = totalWork
                                        notificationManager.notify(
                                            it.name.hashCode(),
                                            NotificationCompat.Builder(this@MainActivity, "Git")
                                                .setContentTitle("${it.name} beginning task $title.")
                                                .setContentText("0 / $totalWork")
                                                .setSmallIcon(R.drawable.ic_launcher_foreground)
                                                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                                                .setAutoCancel(true)
                                                .build()
                                        )
                                    }

                                    override fun update(completed: Int)
                                    {
                                        Log.i(GIT_TAG, "Update $completed")
                                        notificationManager.notify(
                                            it.name.hashCode(),
                                            NotificationCompat.Builder(this@MainActivity, "Git")
                                                .setContentTitle("${it.name} updating.")
                                                .setContentText("$completed / $totalWork")
                                                .setSmallIcon(R.drawable.ic_launcher_foreground)
                                                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                                                .setAutoCancel(true)
                                                .build()
                                        )
                                    }

                                    override fun endTask()
                                    {
                                        Log.i(GIT_TAG, "End")
                                        notificationManager.notify(
                                            it.name.hashCode(),
                                            NotificationCompat.Builder(this@MainActivity, "Git")
                                                .setContentTitle("${it.name} init completed.")
                                                .setContentText("Maybe.")
                                                .setSmallIcon(R.drawable.ic_launcher_foreground)
                                                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                                                .setAutoCancel(true)
                                                .build()
                                        )
                                    }

                                    override fun isCancelled(): Boolean
                                    {
                                        return false
                                    }
                                })
                                .call()
                        }
                        catch (e: Exception)
                        {
                            Log.e(GIT_TAG, "Failed to init ${it.name}", e)
                            notificationManager.notify(
                                it.name.hashCode(),
                                NotificationCompat.Builder(this@MainActivity, "Git")
                                    .setContentTitle("${it.name} init failed.")
                                    .setContentText(e.message)
                                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                                    .setAutoCancel(true)
                                    .build()
                            )
                        }
                    }
                    else
                    {
                        Log.i(GIT_TAG, "Pulling ${it.name}")
                        try
                        {
                            Git.open(this)
                                .pull()
                                .setProgressMonitor(object : ProgressMonitor
                                {
                                    var totalTasks = 0
                                    var totalWork = 0
                                    override fun start(totalTasks: Int)
                                    {
                                        Log.i(GIT_TAG, "Start $totalTasks")
                                        notificationManager.notify(
                                            it.name.hashCode(),
                                            NotificationCompat.Builder(this@MainActivity, "Git")
                                                .setContentTitle("${it.name} start updating.")
                                                .setContentText("0 / $totalTasks")
                                                .setSmallIcon(R.drawable.ic_launcher_foreground)
                                                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                                                .setAutoCancel(true)
                                                .build()
                                        )
                                    }

                                    override fun beginTask(title: String?, totalWork: Int)
                                    {
                                        Log.i(GIT_TAG, "Begin $title $totalWork")
                                        this.totalWork = totalWork
                                        notificationManager.notify(
                                            it.name.hashCode(),
                                            NotificationCompat.Builder(this@MainActivity, "Git")
                                                .setContentTitle("${it.name} beginning task $title.")
                                                .setContentText("0 / $totalWork")
                                                .setSmallIcon(R.drawable.ic_launcher_foreground)
                                                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                                                .setAutoCancel(true)
                                                .build()
                                        )
                                    }

                                    override fun update(completed: Int)
                                    {
                                        Log.i(GIT_TAG, "Update $completed")
                                        notificationManager.notify(
                                            it.name.hashCode(),
                                            NotificationCompat.Builder(this@MainActivity, "Git")
                                                .setContentTitle("${it.name} updating.")
                                                .setContentText("$completed / $totalWork")
                                                .setSmallIcon(R.drawable.ic_launcher_foreground)
                                                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                                                .setAutoCancel(true)
                                                .build()
                                        )
                                    }

                                    override fun endTask()
                                    {
                                        Log.i(GIT_TAG, "End")
                                        notificationManager.notify(
                                            it.name.hashCode(),
                                            NotificationCompat.Builder(this@MainActivity, "Git")
                                                .setContentTitle("${it.name} update completed.")
                                                .setContentText("Maybe.")
                                                .setSmallIcon(R.drawable.ic_launcher_foreground)
                                                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                                                .setAutoCancel(true)
                                                .build()
                                        )
                                    }

                                    override fun isCancelled(): Boolean
                                    {
                                        return false
                                    }
                                })
                                .call()
                        }
                        catch (e: Exception)
                        {
                            Log.e(GIT_TAG, "Failed to update ${it.name}", e)
                            notificationManager.notify(
                                it.name.hashCode(),
                                NotificationCompat.Builder(this@MainActivity, "Git")
                                    .setContentTitle("${it.name} update failed.")
                                    .setContentText(e.message)
                                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                                    .setAutoCancel(true)
                                    .build()
                            )
                        }
                    }
                }
            }
        }
    }

    private fun listEntries() =
        filesDir.listFiles()?.flatMap { repo ->
            repo.listFiles { _, name ->
                !name.startsWith(".")
            }?.map { file ->
                Pair(repo, file.nameWithoutExtension)
            }?: emptyList()
        }
//        filesDir.listFiles()?.mapIndexed { repoIndex, repo ->
//            Pair(repoIndex, Pair(repo, repo.listFiles { _, name ->
//                !name.startsWith(".")
//            }?.mapIndexed { fileIndex, file ->
//                Pair(fileIndex, file.nameWithoutExtension)
//            }))
//        }


    companion object
    {
        private const val ACTIVITY_TAG = "MainActivity"
        private const val GIT_TAG = "Git"
        private const val NetworkBody = "body"
    }
}
