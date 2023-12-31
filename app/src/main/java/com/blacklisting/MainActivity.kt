package com.blacklisting

import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.AdapterView.OnItemSelectedListener
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import com.alibaba.fastjson2.JSONArray
import com.blacklisting.databinding.ActivityMainBinding
import com.blacklisting.ds.github.Org
import com.google.android.material.button.MaterialButton
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

        initSpinner()


        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        gitNotificationChannel = NotificationChannel("Git", "Git Action", NotificationManager.IMPORTANCE_DEFAULT)
        notificationManager.createNotificationChannel(gitNotificationChannel)
    }

    private fun initSpinner()
    {
        binding.selectorContent.adapter = object : BaseAdapter()
        {
            val list = listEntries() ?: emptyList()
            override fun getCount(): Int = list.size

            override fun getItem(position: Int): Any = list[position]

            override fun getItemId(position: Int): Long = position.toLong()

            override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View
            {
                return TextView(this@MainActivity).apply {
                    tag = list[position].first
                    text = "${list[position].first}/${list[position].second}"
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 18.0f)
                }
            }
        }

        binding.selectorContent.onItemSelectedListener = object : OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long)
            {
                val target = File("$filesDir/${(view as? TextView)?.text}.csv")
                val content = target.readText().lines()

                binding.body.removeAllViews()
                val ls = mutableListOf<EditText>()
                content[0].split(",")
                    .map {
                        binding.body.apply body@ {
                            this@body.addView(
                                RelativeLayout(this@MainActivity).apply row@ {
                                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
//                                    this@row.addView(
//                                        TextView(this@MainActivity).apply label@ {
//                                            this@label.layoutParams = RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT)
//                                            this@label.text = it
//                                        }
//                                    )
                                    this@row.addView(
                                        EditText(this@MainActivity).apply edit@ {
                                            this@edit.layoutParams = RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.WRAP_CONTENT)
                                            this@edit.hint = it
                                            ls.add(this)
                                        }
                                    )
                                }
                            )
                        }
                    }
                binding.body.addView(MaterialButton(this@MainActivity).apply {
                    text = "Commit"
                    setOnClickListener {
                        target.appendText("${ls.joinToString(",") { editText -> "\"${editText.text}\"" }}\n")
                        Git.open(File("${filesDir.absolutePath}/${binding.selectorContent.selectedView.tag}"))
                            .add()
                            .addFilepattern(".")
                            .call()
                        Git.open(File("${filesDir.absolutePath}/${binding.selectorContent.selectedView.tag}"))
                            .commit()
                            .setAuthor("blacklisting", "who@knows.me")
                            .setCommitter("blacklisting", "who@knows.me")
                            .setMessage("Update with a comment just seen.")
                            .call()
                    }
                })
            }

            override fun onNothingSelected(parent: AdapterView<*>?)
            {

            }
        }
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
                                        initSpinner()
                                    }

                                    override fun isCancelled(): Boolean
                                    {
                                        return false
                                    }
                                })
                                .call()

                            Git.open(this)
                                .repository
                                .config
                                .setString("user", "", "name", "blacklisting")
                            Git.open(this)
                                .repository
                                .config
                                .setString("user", "", "email", "who@knows.me")

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
                                        initSpinner()
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
            repo.walkTopDown()
                .toList()
                .filter { name ->
                    with(name.toRelativeString(repo)) {
                        isNotBlank() and !startsWith(".") and endsWith(".csv")
                    }
                }
                .map { file ->
                    Pair(repo.name, file.toRelativeString(repo).substringBefore(".csv"))
                }
        }


    companion object
    {
        private const val ACTIVITY_TAG = "MainActivity"
        private const val GIT_TAG = "Git"
        private const val NetworkBody = "body"
    }
}
