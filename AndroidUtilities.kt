@file:Suppress("unused")

package com.theevilroot.androidutilities

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.os.Build
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.annotation.LayoutRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.RecyclerView


//
// Android Kotlin Utilities
// Author: TheEvilRoot
//

/** UI Utilities **/

fun Context.toast(
    message: Any,
    time: Int = Toast.LENGTH_SHORT) {
    Toast.makeText(this, message.toString(), time).show()
}

inline fun <reified T: Activity> Context.open() = startActivity(Intent(this, T::class.java))

/** Other stuff **/

fun Context.unwrapStringRes(@StringRes id: Int?, defaultString: String?): String? {
    id?.let {
        return try {
            getString(it)
        } catch (e: Resources.NotFoundException) {
            defaultString
        }
    }

    return defaultString
}

inline fun withDebug(body: () -> Unit) {
    if (BuildConfig.DEBUG) {
        body()
    }
}

@SuppressLint("MissingPermission")
fun Context.checkNetwork(): Boolean {
    using(permissions = setOf(Manifest.permission.ACCESS_NETWORK_STATE), block = {
        val connectivityService = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkInfo = connectivityService.activeNetworkInfo
        networkInfo?.let {
            return it.detailedState == NetworkInfo.DetailedState.CONNECTED ||
                    it.detailedState == NetworkInfo.DetailedState.CONNECTING
        } ?: return false
    }, denied = {
        return false
    })
    return false
}

inline fun EditText.afterTextChanged(crossinline listener: (String) -> Unit) {
    addTextChangedListener(object: TextWatcher {
        override fun beforeTextChanged(
            s: CharSequence?,
            start: Int,
            count: Int,
            after: Int
        ) { }

        override fun onTextChanged(
            s: CharSequence?,
            start: Int,
            before: Int,
            count: Int
        ) { }

        override fun afterTextChanged(s: Editable) {
            listener(s.toString())
        }
    })
}

/** Permission and capability utilities **/

fun Context.checkPermissionIsGranted(permission: String): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        checkSelfPermission(permission)
    } else {
        packageManager.checkPermission(permission, packageName)
    } == PackageManager.PERMISSION_GRANTED
}

enum class UsingResult { OK, SDK_ERROR, PERMISSION_ERROR }

inline fun Context.using(sdk: Int? = null, permissions: Set<String> = emptySet(),  denied: Context.(UsingResult) -> Unit = { }, block: Context.() -> Unit) {
    if (sdk != null && Build.VERSION.SDK_INT < sdk) return denied(UsingResult.SDK_ERROR)

    if (permissions.any { !checkPermissionIsGranted(it) }) {
        return denied(UsingResult.PERMISSION_ERROR)
    }

    block()
}

inline fun withSupport(version: Int, func: () -> Unit) {
    if (Build.VERSION.SDK_INT >= version) {
        func()
    }
}

/** Dialog utilities **/

fun AlertDialog.positiveButton(): Button?
        = getButton(AlertDialog.BUTTON_POSITIVE)

fun AlertDialog.negativeButton(): Button?
        = getButton(AlertDialog.BUTTON_NEGATIVE)

fun AlertDialog.neutralButton(): Button?
        = getButton(AlertDialog.BUTTON_NEUTRAL)

fun Context.messageDialog(
    title: String,
    message: String,
    okButtonText: String? = "Ok",
    @StringRes okButtonTextRes: Int? = null,
    cancelButtonText: String? = null,
    @StringRes cancelButtonTextRes: Int? = null,
    okAction: (DialogInterface) -> Unit = { },
    cancelAction: (DialogInterface) -> Unit = { di -> di.cancel() },
    cancelable: Boolean = true
) {
    val dialogBuilder = AlertDialog.Builder(this)
    dialogBuilder.setTitle(title)
    dialogBuilder.setMessage(message)

    unwrapStringRes(okButtonTextRes, okButtonText)?.let { okText ->
        dialogBuilder.setPositiveButton(okText) { di, _ ->
            okAction(di)
        }
    }

    unwrapStringRes(cancelButtonTextRes, cancelButtonText)?.let { cancelText ->
        dialogBuilder.setNegativeButton(cancelText) { di, _ ->
            cancelAction(di)
        }
    }

    dialogBuilder.setCancelable(cancelable)
    val dialog = dialogBuilder.create()
    dialog.show()
}

fun <T> Context.listDialog(
    title: String,
    items: Array<T>,
    inlineItem: (T, Int) -> String = { t, _ -> t.toString() },
    itemListener: (DialogInterface, T, Int) -> Unit = { _, _, _ -> },
    cancelable: Boolean = false
) {
    val dialogBuilder = AlertDialog.Builder(this)
    dialogBuilder.setTitle(title)

    val inlinedItems = items.mapIndexed { index, t -> inlineItem(t, index) }.toTypedArray()
    dialogBuilder.setItems(inlinedItems) { di, index ->
        itemListener(di, items[index], index)
    }

    dialogBuilder.setCancelable(cancelable)

    val dialog = dialogBuilder.create()
    dialog.show()
}

fun Context.inputDialog(
    title: String,
    prepareLayout: (EditText, TextView) -> Unit = { _, _ -> },
    okButtonText: String? = "Ok",
    okButtonTextRes: Int? = null,
    cancelButtonText: String? = null,
    cancelButtonTextRes: Int? = null,
    okAction: (DialogInterface, String) -> Unit = { _, _ -> },
    cancelAction: (DialogInterface, String) -> Unit = { _, _ -> },
    textChangeListener: ((AlertDialog, String, EditText, TextView) -> Unit)? = null,
    cancelable: Boolean = false
) {
    val dialogBuilder = AlertDialog.Builder(this)
    dialogBuilder.setTitle(title)

    val view = LinearLayout(this)
    val viewParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
    view.orientation = LinearLayout.VERTICAL
    view.layoutParams = viewParams
    view.setPadding(16,8,16,8)

    val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
    val labelTextView = TextView(this)
    labelTextView.layoutParams = params

    val inputEditText = EditText(this)
    inputEditText.layoutParams = params

    view.addView(labelTextView)
    view.addView(inputEditText)

    dialogBuilder.setView(view)

    prepareLayout(inputEditText, labelTextView)

    unwrapStringRes(okButtonTextRes, okButtonText)?.let { okText ->
        dialogBuilder.setPositiveButton(okText) { di, _ ->
            okAction(di, inputEditText.text.toString())
        }
    }

    unwrapStringRes(cancelButtonTextRes, cancelButtonText)?.let { cancelText ->
        dialogBuilder.setNegativeButton(cancelText) { di, _ ->
            cancelAction(di, inputEditText.text.toString())
        }
    }

    dialogBuilder.setCancelable(cancelable)

    val dialog = dialogBuilder.create()

    textChangeListener?.let { listener ->
        inputEditText.afterTextChanged {
            listener(dialog, it, inputEditText, labelTextView)
        }
    }

    dialog.show()
}

/** Logger utilities **/

fun Unit.info(message: String) {
    Log.i(javaClass.simpleName, message)
}

fun Unit.error(message: String, throwable: Throwable) {
    Log.e(javaClass.simpleName, message, throwable)
}

fun Unit.debug(message: String) {
    withDebug { Log.d(javaClass.simpleName, message) }
}

/** View utilities **/

fun View.isVisible(): Boolean =
    visibility == View.VISIBLE && alpha > 0F && scaleX > 0F && scaleY > 0F

/** Animations **/

fun View.scaleHighlight(maxScaleMultiplier: Float = 2F) {
    animate().scaleXBy(1F).scaleYBy(1F).scaleX(maxScaleMultiplier).scaleY(maxScaleMultiplier).setDuration(300)
        .withEndAction {
            animate().scaleX(1F).scaleY(1F).setDuration(300).start()
        }.start()
}

inline fun View.fadeHide(time: Long = 2000, crossinline finish: () -> Unit = { }) {
    animate().setDuration(time).alpha(0F).withEndAction { finish() }.start()
}

inline fun View.fadeShow(time: Long = 2000,forceZeroAlpha: Boolean = true, crossinline finish: () -> Unit = { }) {
    animate().apply {
        if(forceZeroAlpha) alphaBy(0F)
    }.alpha(1F).setDuration(time).withEndAction { finish() }.start()
}

inline fun View.scaleShow(time: Long = 2000, forceZeroScale: Boolean = true, crossinline finish: () -> Unit = { }) {
    animate().apply {
        if (forceZeroScale) {
            scaleXBy(0F)
            scaleYBy(0F)
        }
    }.scaleX(1F).scaleY(1F).setDuration(time).withEndAction { finish() }.start()
}

inline fun View.scaleHide(time: Long = 2000, crossinline finish: () -> Unit = { }) {
    animate().scaleX(0F).scaleY(0F).setDuration(time).withEndAction { finish() }.start()
}

/** RecyclerView Utilities **/

class BasicRecyclerViewAdapter<T>(
    private val getter: (Int) -> T,
    private val countGetter: () -> Int,
    @LayoutRes
    private val layoutRes: Int,
    private val binding: View.(Int, T, BasicRecyclerViewAdapter.BasicViewHolder<T>) -> Unit): RecyclerView.Adapter<BasicRecyclerViewAdapter.BasicViewHolder<T>>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BasicViewHolder<T> =
        BasicViewHolder(LayoutInflater.from(parent.context).
            inflate(layoutRes, parent, false))

    override fun getItemCount(): Int =
        countGetter()

    override fun onBindViewHolder(holder: BasicViewHolder<T>, position: Int) =
            binding(holder.itemView, position, getter(position), holder)

    class BasicViewHolder<T>(itemView: View): RecyclerView.ViewHolder(itemView)
}