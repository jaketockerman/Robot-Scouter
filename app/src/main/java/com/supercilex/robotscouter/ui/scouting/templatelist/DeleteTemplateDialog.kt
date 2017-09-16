package com.supercilex.robotscouter.ui.scouting.templatelist

import android.app.Dialog
import android.os.Bundle
import android.support.v4.app.FragmentManager
import android.support.v7.app.AlertDialog
import android.text.TextUtils
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.supercilex.robotscouter.R
import com.supercilex.robotscouter.data.model.DEFAULT_TEMPLATE_TYPE
import com.supercilex.robotscouter.util.FIRESTORE_TEMPLATE_ID
import com.supercilex.robotscouter.util.data.TeamsLiveData
import com.supercilex.robotscouter.util.data.defaultTemplateId
import com.supercilex.robotscouter.util.data.firestoreBatch
import com.supercilex.robotscouter.util.data.getRef
import com.supercilex.robotscouter.util.data.model.deleteTemplate
import com.supercilex.robotscouter.util.data.model.ref
import com.supercilex.robotscouter.util.data.observeOnDataChanged
import com.supercilex.robotscouter.util.data.observeOnce
import com.supercilex.robotscouter.util.data.putRef
import com.supercilex.robotscouter.util.ui.ManualDismissDialog
import com.supercilex.robotscouter.util.ui.show

class DeleteTemplateDialog : ManualDismissDialog() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog = AlertDialog.Builder(context)
            .setTitle(R.string.confirm_action)
            .setMessage(R.string.delete_template_warning)
            .setPositiveButton(R.string.delete_template, null)
            .setNegativeButton(android.R.string.cancel, null)
            .createAndSetup()

    override fun onAttemptDismiss(): Boolean {
        val templateId = arguments.getRef().id
        TeamsLiveData.observeOnDataChanged().observeOnce { teams ->
            firestoreBatch {
                teams.filter { TextUtils.equals(templateId, it.templateId) }.forEach {
                    update(it.ref, FIRESTORE_TEMPLATE_ID, FieldValue.delete())
                }
            }

            if (templateId == defaultTemplateId) defaultTemplateId = DEFAULT_TEMPLATE_TYPE
            deleteTemplate(templateId)

            Tasks.forResult(null)
        }
        return true
    }

    companion object {
        private const val TAG = "DeleteTemplateDialog"

        fun show(manager: FragmentManager, templateRef: DocumentReference) =
                DeleteTemplateDialog().show(manager, TAG) { putRef(templateRef) }
    }
}
