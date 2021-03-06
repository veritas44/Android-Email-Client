package com.criptext.mail.utils.ui

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.criptext.mail.R
import com.criptext.mail.utils.ui.data.DialogResult
import com.criptext.mail.utils.ui.data.DialogType
import com.criptext.mail.utils.uiobserver.UIObserver

class AccountSuspendedDialog(val context: Context) {

    private var dialog: AlertDialog? = null
    private val res = context.resources
    private lateinit var btnOk: Button

    private lateinit var view: View

    fun showDialog(observer: UIObserver?, email: String, type: DialogType) {

        val dialogBuilder = AlertDialog.Builder(context)
        val inflater = (context as AppCompatActivity).layoutInflater
        view = inflater.inflate(R.layout.enterprise_account_suspended_dialog, null)
        view.findViewById<TextView>(R.id.account_suspended_message).text =
                context.getString(R.string.account_suspended_dialog_message, email)
        btnOk = view.findViewById(R.id.switch_account_button) as Button
        when(type){
            is DialogType.SwitchAccount -> {
                btnOk.text = context.getString(R.string.account_suspended_dialog_button)
            }
            is DialogType.SignIn -> {
                btnOk.text = context.getString(R.string.login)
            }
        }
        btnOk.setOnClickListener {
            observer?.onGeneralOkButtonPressed(DialogResult.DialogConfirmation(type))
        }

        dialogBuilder.setView(view)

        dialog = createDialog(view, dialogBuilder)
    }

    private fun createDialog(dialogView: View, dialogBuilder: AlertDialog.Builder): AlertDialog {

        val width = res.getDimension(R.dimen.password_login_dialog_width).toInt()
        val newLogoutDialog = dialogBuilder.create()
        val window = newLogoutDialog.window
        newLogoutDialog.show()
        window?.setLayout(width, LinearLayout.LayoutParams.WRAP_CONTENT)
        window?.setGravity(Gravity.CENTER_VERTICAL)
        val drawableBackground = ContextCompat.getDrawable(dialogView.context,
                R.drawable.dialog_label_chooser_shape)
        newLogoutDialog.window?.setBackgroundDrawable(drawableBackground)
        newLogoutDialog.setCancelable(false)
        newLogoutDialog.setCanceledOnTouchOutside(false)



        return newLogoutDialog
    }

    fun dismissDialog(){
        dialog?.dismiss()
    }
}
