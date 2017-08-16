package com.supercilex.robotscouter.ui.scouting

import android.arch.lifecycle.MutableLiveData
import com.google.firebase.crash.FirebaseCrash
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ValueEventListener
import com.supercilex.robotscouter.util.data.ViewModelBase

class TabKeysHolder : ViewModelBase<DatabaseReference>(), ValueEventListener {
    val keysListener = MutableLiveData<List<String>>()
    lateinit var ref: DatabaseReference

    override fun onCreate(args: DatabaseReference) {
        ref = args
        ref.addValueEventListener(this)
    }

    override fun onDataChange(snapshot: DataSnapshot) {
        keysListener.value = snapshot.children.map { it.key }.reversed()
    }

    override fun onCleared() {
        super.onCleared()
        ref.removeEventListener(this)
    }

    override fun onCancelled(error: DatabaseError) = FirebaseCrash.report(error.toException())
}
