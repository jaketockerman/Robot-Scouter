package com.supercilex.robotscouter.ui.scouting.scoutlist

import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.content.Intent
import android.os.Bundle
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.firebase.appindexing.FirebaseUserActions
import com.google.firebase.firestore.DocumentSnapshot
import com.supercilex.robotscouter.R
import com.supercilex.robotscouter.data.model.Team
import com.supercilex.robotscouter.data.model.TemplateType
import com.supercilex.robotscouter.ui.ShouldUploadMediaToTbaDialog
import com.supercilex.robotscouter.ui.TeamDetailsDialog
import com.supercilex.robotscouter.ui.TeamSharer
import com.supercilex.robotscouter.ui.scouting.templatelist.TemplateListActivity
import com.supercilex.robotscouter.util.CrashLogger
import com.supercilex.robotscouter.util.asLifecycleReference
import com.supercilex.robotscouter.util.await
import com.supercilex.robotscouter.util.data.KEY_ADD_SCOUT
import com.supercilex.robotscouter.util.data.KEY_OVERRIDE_TEMPLATE_KEY
import com.supercilex.robotscouter.util.data.getScoutBundle
import com.supercilex.robotscouter.util.data.getTabId
import com.supercilex.robotscouter.util.data.model.TeamHolder
import com.supercilex.robotscouter.util.data.model.addScout
import com.supercilex.robotscouter.util.data.model.getTemplatesQuery
import com.supercilex.robotscouter.util.data.scoutParser
import com.supercilex.robotscouter.util.data.viewAction
import com.supercilex.robotscouter.util.isOffline
import com.supercilex.robotscouter.util.logFailures
import com.supercilex.robotscouter.util.ui.CaptureTeamMediaListener
import com.supercilex.robotscouter.util.ui.FragmentBase
import com.supercilex.robotscouter.util.ui.RecyclerPoolHolder
import com.supercilex.robotscouter.util.ui.TemplateSelectionListener
import com.supercilex.robotscouter.util.unsafeLazy
import kotlinx.android.synthetic.main.fragment_scout_list.*
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.async
import org.jetbrains.anko.design.longSnackbar
import org.jetbrains.anko.support.v4.find

abstract class ScoutListFragmentBase : FragmentBase(), RecyclerPoolHolder,
        TemplateSelectionListener, Observer<Team>, CaptureTeamMediaListener {
    override val recyclerPool = RecyclerView.RecycledViewPool()

    protected lateinit var viewHolder: AppBarViewHolderBase
        private set

    protected val dataHolder: TeamHolder by unsafeLazy {
        ViewModelProviders.of(this).get(TeamHolder::class.java)
    }
    private lateinit var team: Team
    // It's not a lateinit because it could be used before initialization
    var pagerAdapter: ScoutPagerAdapter? = null

    protected var onScoutingReadyTask = TaskCompletionSource<Nothing?>()
    private var savedState: Bundle? = null

    private val scoutId: String?
        get() {
            var scoutId: String? = pagerAdapter?.currentTabId
            if (scoutId == null && savedState != null) scoutId = getTabId(savedState!!)
            if (scoutId == null) scoutId = getTabId(arguments)
            return scoutId
        }
    protected val bundle: Bundle
        get() = getScoutBundle(team, arguments!!.getBoolean(KEY_ADD_SCOUT), scoutId = scoutId)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
        savedState = savedInstanceState

        dataHolder.init(savedInstanceState ?: arguments!!)
        team = dataHolder.teamListener.value!!
        dataHolder.teamListener.observe(this, this)
    }

    override fun onChanged(team: Team?) {
        if (team == null) {
            onTeamDeleted()
        } else {
            this.team = team
            if (!onScoutingReadyTask.task.isComplete) {
                initScoutList()
                onScoutingReadyTask.setResult(null)
            }
        }
    }

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View = View.inflate(context, R.layout.fragment_scout_list, null)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        if (savedInstanceState == null && isOffline) {
            longSnackbar(view, R.string.scout_offline_rationale)
        }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        viewHolder = newViewModel(savedInstanceState)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        viewHolder.initMenu()
    }

    override fun onStart() {
        super.onStart()
        FirebaseUserActions.getInstance().start(team.viewAction).logFailures()
    }

    override fun onStop() {
        super.onStop()
        FirebaseUserActions.getInstance().end(team.viewAction).logFailures()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        pagerAdapter?.onSaveInstanceState(outState)
        dataHolder.onSaveInstanceState(outState)
        viewHolder.onSaveInstanceState(outState)
    }

    override fun onRequestPermissionsResult(
            requestCode: Int,
            permissions: Array<String>,
            grantResults: IntArray
    ) = viewHolder.onRequestPermissionsResult(requestCode, permissions, grantResults)

    override fun startCapture(shouldUploadMediaToTba: Boolean) =
            viewHolder.startCapture(shouldUploadMediaToTba)

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) =
            viewHolder.onActivityResult(requestCode, resultCode, data)

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_new_scout -> addScout()
            R.id.action_add_media -> ShouldUploadMediaToTbaDialog.show(this)
            R.id.action_share -> TeamSharer.shareTeams(this, listOf(team))
            R.id.action_edit_template -> {
                val templateId = team.templateId

                TemplateType.coerce(templateId)?.let {
                    startActivity(TemplateListActivity.createIntent(it.id.toString()))
                    return true
                }

                val ref = asLifecycleReference()
                async(UI) {
                    val ownsTemplate = try {
                        async { getTemplatesQuery().get().await() }.await()
                    } catch (e: Exception) {
                        CrashLogger.onFailure(e)
                        emptyList<DocumentSnapshot>()
                    }.map {
                        scoutParser.parseSnapshot(it)
                    }.find { it.id == templateId } != null

                    if (ownsTemplate) {
                        ref().startActivity(TemplateListActivity.createIntent(templateId))
                    } else {
                        longSnackbar(ref().find(R.id.root), R.string.template_access_denied_error)
                    }
                }.logFailures()
            }
            R.id.action_edit_team_details -> showTeamDetails()
            else -> return false
        }
        return true
    }

    fun showTeamDetails() = TeamDetailsDialog.show(childFragmentManager, team)

    fun addScoutWithSelector() = ScoutTemplateSelectorDialog.show(childFragmentManager)

    fun addScout(id: String? = null) {
        pagerAdapter!!.apply {
            currentTabId = team.addScout(id, holder.scouts)
        }
    }

    override fun onTemplateSelected(id: String) = addScout(id)

    private fun initScoutList() {
        pagerAdapter = ScoutPagerAdapter(this, team)
        pagerAdapter!!.currentTabId = scoutId

        viewPager.adapter = pagerAdapter
        tabs.setupWithViewPager(viewPager)

        arguments!!.let {
            if (it.getBoolean(KEY_ADD_SCOUT, false)) {
                it.remove(KEY_ADD_SCOUT)
                addScout(it.getString(KEY_OVERRIDE_TEMPLATE_KEY, null))
            }
        }
    }

    protected abstract fun newViewModel(savedInstanceState: Bundle?): AppBarViewHolderBase

    protected abstract fun onTeamDeleted()
}
