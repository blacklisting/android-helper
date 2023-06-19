package com.blacklisting

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.Menu
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

    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initOrPullRepos()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean
    {
        menuInflater.inflate(R.menu.main, menu)

        return true
    }

    private val simpleProgressMonitor = object : ProgressMonitor
    {
        override fun start(totalTasks: Int)
        {
            Log.i(GIT_TAG, "Start $totalTasks")

        }

        override fun beginTask(title: String?, totalWork: Int)
        {
            Log.i(GIT_TAG, "Begin $title $totalWork")
        }

        override fun update(completed: Int)
        {
            Log.i(GIT_TAG, "Update $completed")
        }

        override fun endTask()
        {
            Log.i(GIT_TAG, "End")
        }

        override fun isCancelled(): Boolean
        {
            return false
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
                        Log.i(GIT_TAG, "Cloning ${it.name}")
                        try
                        {
                            Git.cloneRepository()
                                .setDirectory(this)
                                .setURI(it.cloneUrl)
                                .setProgressMonitor(simpleProgressMonitor)
                                .call()
                        }
                        catch (e: Exception)
                        {
                            Log.e(GIT_TAG, "Failed to init ${it.name}", e)
                        }
                    }
                    else
                    {
                        Log.i(GIT_TAG, "Pulling ${it.name}")
                        try
                        {
                            Git.open(this)
                                .pull()
                                .setProgressMonitor(simpleProgressMonitor)
                                .call()
                        }
                        catch (e: Exception)
                        {
                            Log.e(GIT_TAG, "Failed to update ${it.name}", e)
                        }
                    }
                }
            }
        }
    }

    private fun listEntries() =
        filesDir.listFiles()?.mapIndexed { repoIndex, repo ->
            Pair(repoIndex, Pair(repo, repo.listFiles { _, name ->
                !name.startsWith(".")
            }?.mapIndexed { fileIndex, file ->
                Pair(fileIndex, file.nameWithoutExtension)
            }))
        }

    companion object
    {
        private const val ACTIVITY_TAG = "MainActivity"
        private const val GIT_TAG = "Git"
        private const val NetworkBody = "body"
    }
}