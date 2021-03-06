package com.supercilex.robotscouter.data.client

import com.firebase.ui.auth.IdpResponse
import com.firebase.ui.auth.util.accountlink.ManualMergeService
import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.QuerySnapshot
import com.supercilex.robotscouter.util.FIRESTORE_ACTIVE_TOKENS
import com.supercilex.robotscouter.util.FIRESTORE_NUMBER
import com.supercilex.robotscouter.util.FIRESTORE_TIMESTAMP
import com.supercilex.robotscouter.util.asTask
import com.supercilex.robotscouter.util.await
import com.supercilex.robotscouter.util.data.firestoreBatch
import com.supercilex.robotscouter.util.data.generateToken
import com.supercilex.robotscouter.util.data.model.getTemplatesQuery
import com.supercilex.robotscouter.util.data.model.teamsQuery
import com.supercilex.robotscouter.util.data.updateOwner
import com.supercilex.robotscouter.util.logCrashLog
import com.supercilex.robotscouter.util.logFailures
import com.supercilex.robotscouter.util.uid
import kotlinx.coroutines.experimental.async
import java.util.Date
import com.supercilex.robotscouter.util.data.model.userPrefs as userPrefsRef

class AccountMergeService : ManualMergeService() {
    private lateinit var token: String

    private lateinit var prevUid: String
    private lateinit var userPrefs: QuerySnapshot

    private lateinit var teams: QuerySnapshot
    private lateinit var templates: QuerySnapshot

    override fun onLoadData(): Task<Void?>? {
        token = generateToken
        prevUid = uid!!

        logCrashLog("Migrating user data from $prevUid.")

        return async {
            await(
                    async { userPrefs = userPrefsRef.get().await() },
                    async { teams = teamsQuery.get().await() },
                    async { templates = getTemplatesQuery().get().await() }
            )

            val tokenPath = FieldPath.of(FIRESTORE_ACTIVE_TOKENS, token)
            val refs = (teams + templates).map { it.reference }
            firestoreBatch {
                for (ref in refs) update(ref, tokenPath, Date())
            }.logFailures(refs, token).await()
        }.logFailures().asTask()
    }

    override fun onTransferData(response: IdpResponse): Task<Void?>? = async {
        for (snapshot in userPrefs) {
            userPrefsRef.document(snapshot.id).set(snapshot.data)
                    .logFailures(snapshot.reference, snapshot.data)
        }

        val tokenPath = FieldPath.of(FIRESTORE_ACTIVE_TOKENS, token)

        val teamRefs = teams.map { it.reference }
        async {
            updateOwner(teamRefs, token, prevUid) { ref ->
                teams.find { it.reference.path == ref.path }!!.getLong(FIRESTORE_NUMBER)
            }
            for (ref in teamRefs) {
                ref.update(tokenPath, FieldValue.delete()).logFailures(ref, token)
            }
        }.logFailures()

        val templateRefs = templates.map { it.reference }
        async {
            updateOwner(templateRefs, token, prevUid) { ref ->
                templates.find { it.reference.path == ref.path }!!.getDate(FIRESTORE_TIMESTAMP)
            }
            for (ref in templateRefs) {
                ref.update(tokenPath, FieldValue.delete()).logFailures(ref, token)
            }
        }.logFailures()

        null
    }.asTask()
}
